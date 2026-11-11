package com.denhub.service;

import com.denhub.domain.*;
import com.denhub.repository.*;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class ChannelFeaturesService {
    private final MessageRepository messages;
    private final ChannelRepository channels;
    private final ServerRepository servers;
    private final ServerMemberRepository members;
    private final UserRepository users;
    private final MessagePinRepository pins;
    private final SavedMessageRepository saved;
    private final ChannelNotificationPreferenceRepository preferences;
    private final MessageService messageService;
    private final ApplicationEventPublisher events;

    public ChannelFeaturesService(MessageRepository messages, ChannelRepository channels, ServerRepository servers,
            ServerMemberRepository members, UserRepository users, MessagePinRepository pins,
            SavedMessageRepository saved, ChannelNotificationPreferenceRepository preferences,
            MessageService messageService, ApplicationEventPublisher events) {
        this.messages=messages; this.channels=channels; this.servers=servers; this.members=members; this.users=users;
        this.pins=pins; this.saved=saved; this.preferences=preferences; this.messageService=messageService; this.events=events;
    }
    private ResponseStatusException error(HttpStatus status,String detail) { return new ResponseStatusException(status,detail); }
    private User user() {
        String email=SecurityUtils.getCurrentUserLogin().orElseThrow(() -> error(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập"));
        return users.findOneByEmail(email).filter(u -> Boolean.TRUE.equals(u.getActivated()))
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED,"Phiên đăng nhập không hợp lệ"));
    }
    private Channel access(Long id, User user, boolean writing) {
        if(id==null || id<=0) throw error(HttpStatus.BAD_REQUEST,"ID kênh không hợp lệ");
        Channel channel=channels.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy kênh"));
        if(writing) servers.findByIdForUpdate(channel.getServerId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Không tìm thấy Server"));
        if(!members.existsByServerIdAndUserId(channel.getServerId(),user.getId())) throw error(HttpStatus.FORBIDDEN,"Bạn không có quyền truy cập kênh");
        return channel;
    }
    private Message message(Long id, User user) {
        if(id==null || id<=0) throw error(HttpStatus.BAD_REQUEST,"ID tin nhắn không hợp lệ");
        Message message=messages.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Tin nhắn không còn tồn tại"));
        if(message.getChannelId()==null || message.getConversationId()!=null) throw error(HttpStatus.BAD_REQUEST,"Chỉ áp dụng cho tin nhắn nhóm");
        access(message.getChannelId(),user,true);
        if (!messages.existsById(id)) throw error(HttpStatus.NOT_FOUND,"Tin nhắn không còn tồn tại");
        return message;
    }
    private int limit(Integer size,Long before) {
        if(before!=null && before<=0) throw error(HttpStatus.BAD_REQUEST,"Con trỏ không hợp lệ");
        int value=size==null?30:size;
        if(value<1 || value>100) throw error(HttpStatus.BAD_REQUEST,"Số lượng phải từ 1 đến 100");
        return value;
    }
    private CursorPageResponse<MessageResponse> page(List<Message> rows,int limit) {
        boolean more=rows.size()>limit;
        List<Message> selected=more?rows.subList(0,limit):rows;
        return new CursorPageResponse<>(selected.stream().map(messageService::describeMessage).toList(),
                more?selected.getLast().getId():null,more,limit);
    }
    public CursorPageResponse<MessageResponse> search(Long channelId,String query,Long before,Integer size) {
        User user=user(); access(channelId,user,false); int limit=limit(size,before);
        if(query==null || query.strip().isEmpty() || query.strip().length()>200) throw error(HttpStatus.BAD_REQUEST,"Từ khóa phải từ 1 đến 200 ký tự");
        String pattern=query.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_").replace("[","![");
        return page(messages.searchChannel(channelId,"%"+pattern+"%",before,PageRequest.of(0,limit+1)),limit);
    }
    public CursorPageResponse<MessageResponse> pins(Long channelId,Long before,Integer size) {
        access(channelId,user(),false); int limit=limit(size,before);
        return page(messages.pinned(channelId,before,PageRequest.of(0,limit+1)),limit);
    }
    public CursorPageResponse<MessageResponse> saved(Long before,Integer size) {
        User user=user(); int limit=limit(size,before);
        return page(messages.saved(user.getId(),before,PageRequest.of(0,limit+1)),limit);
    }
    @Transactional
    public void pin(Long messageId,boolean enabled) {
        User user=user(); Message message=message(messageId,user);
        Channel channel=channels.findById(message.getChannelId()).orElseThrow();
        Server server=servers.findByIdForUpdate(channel.getServerId()).orElseThrow();
        if(!user.getId().equals(server.getOwnerId())) throw error(HttpStatus.FORBIDDEN,"Chỉ chủ Server được ghim tin nhắn");
        Optional<MessagePin> existing=pins.findByMessageId(messageId);
        if(enabled && existing.isEmpty()) {
            MessagePin pin=new MessagePin(); pin.setMessageId(messageId); pin.setPinnedBy(user.getId()); pin.setPinnedAt(Instant.now()); pins.save(pin);
        } else if(!enabled && existing.isPresent()) pins.delete(existing.get());
        events.publishEvent(new FeatureChanged(message.getChannelId(),null,null,"PINS_CHANGED"));
    }
    @Transactional
    public void save(Long messageId,boolean enabled) {
        User user=user(); message(messageId,user);
        Optional<SavedMessage> existing=saved.findByUserIdAndMessageId(user.getId(),messageId);
        if(enabled && existing.isEmpty()) {
            SavedMessage item=new SavedMessage(); item.setUserId(user.getId()); item.setMessageId(messageId); item.setSavedAt(Instant.now()); saved.save(item);
        } else if(!enabled && existing.isPresent()) saved.delete(existing.get());
        events.publishEvent(new FeatureChanged(null,user.getId(),user.getEmail(),"SAVED_CHANGED"));
    }
    public Preference preference(Long channelId) {
        User user=user(); access(channelId,user,false);
        return new Preference(channelId,preferences.findByUserIdAndChannelId(user.getId(),channelId)
                .map(ChannelNotificationPreference::getMuted).orElse(false));
    }
    @Transactional
    public Preference preference(Long channelId,boolean muted) {
        User user=user(); access(channelId,user,true);
        var preference=preferences.findByUserIdAndChannelId(user.getId(),channelId).orElseGet(ChannelNotificationPreference::new);
        preference.setUserId(user.getId()); preference.setChannelId(channelId); preference.setMuted(muted); preferences.save(preference);
        events.publishEvent(new FeatureChanged(channelId,user.getId(),user.getEmail(),"PREFERENCE_CHANGED"));
        return new Preference(channelId,muted);
    }
    public record Preference(Long channelId,boolean muted) {}
    public record FeatureChanged(Long channelId,Long userId,String email,String eventType) {}
}
