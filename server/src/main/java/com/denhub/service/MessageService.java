package com.denhub.service;

import com.denhub.domain.Channel;
import com.denhub.domain.Message;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.CursorPageResponse;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import com.denhub.service.dto.UpdateMessageRequest;
import com.denhub.service.realtime.ChannelMessageDelivery;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final UserRepository userRepository;
    private final Validator validator;
    private final ServerRepository serverRepository;
    private final ApplicationEventPublisher events;

    public MessageService(MessageRepository messageRepository,
                          ChannelRepository channelRepository,
                          ServerMemberRepository serverMemberRepository,
                          UserRepository userRepository,
                          Validator validator,
                          ServerRepository serverRepository,
                          ApplicationEventPublisher events) {
        this.messageRepository = messageRepository;
        this.channelRepository = channelRepository;
        this.serverMemberRepository = serverMemberRepository;
        this.userRepository = userRepository;
        this.validator = validator;
        this.serverRepository = serverRepository;
        this.events = events;
    }

    @Transactional
    public MessageResponse sendMessage(Long channelId, SendMessageRequest request) {
        String email = getAuthenticatedEmail();
        validateRequest(channelId, request);

        User sender = userRepository.findOneByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));

        return persistMessage(channelId, request, sender);
    }

    /**
     * Gửi tin nhắn từ STOMP: danh tính lấy từ Principal của phiên WebSocket
     * (User ID dạng số hoặc email), không tin dữ liệu từ client.
     */
    @Transactional
    public MessageResponse sendMessageFromWebSocket(Long channelId, SendMessageRequest request, String principalName) {
        validateRequest(channelId, request);
        User sender = resolveActiveUser(principalName);
        return persistMessage(channelId, request, sender);
    }

    /** Kiểm tra Principal WebSocket là thành viên của Server chứa Channel (dùng khi SUBSCRIBE). */
    @Transactional(readOnly = true)
    public boolean isChannelMember(Long channelId, String principalName) {
        if (channelId == null || principalName == null || principalName.isBlank()) {
            return false;
        }
        try {
            User user = resolveActiveUser(principalName);
            return channelRepository.findById(channelId)
                    .map(channel -> serverMemberRepository.existsByServerIdAndUserId(channel.getServerId(), user.getId()))
                    .orElse(false);
        } catch (ResponseStatusException ex) {
            return false;
        }
    }

    private User resolveActiveUser(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        String name = principalName.trim();
        java.util.Optional<User> found;
        if (name.chars().allMatch(Character::isDigit)) {
            try {
                found = userRepository.findById(Long.parseLong(name));
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
            }
        } else {
            found = userRepository.findOneByEmail(name.toLowerCase(Locale.ENGLISH));
        }
        return found
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
    }

    private MessageResponse persistMessage(Long channelId, SendMessageRequest request, User sender) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy Channel"));

        // Serialize channel writes with deletion, including requests that read the channel earlier.
        serverRepository.findByIdForUpdate(channel.getServerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Server"));

        if (!serverMemberRepository.existsByServerIdAndUserId(channel.getServerId(), sender.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền gửi tin nhắn trong Channel này");
        }

        Message message = new Message();
        message.setChannelId(channel.getId());
        message.setAuthorId(sender.getId());
        message.setContent(request.content());
        if (request.getReplyToMessageId() != null) {
            Message target = messageRepository.findById(request.getReplyToMessageId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin nhắn gốc không còn tồn tại"));
            if (!channelId.equals(target.getChannelId()) || target.getConversationId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ trả lời tin nhắn cùng kênh");
            }
            message.setReplyToMessageId(target.getId());
        }

        Message savedMessage = messageRepository.save(message);
        MessageResponse response = describeMessage(savedMessage);
        response.setUpdatedAt(null);
        events.publishEvent(new ChannelMessageDelivery.Created(response));
        return response;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(Long channelId, Long before, Integer limit) {
        String email = getAuthenticatedEmail();

        if (channelId == null || channelId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID của Channel không hợp lệ");
        }

        int effectiveLimit = (limit != null) ? limit : 30;
        if (effectiveLimit < 1 || effectiveLimit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng tin nhắn phải từ 1 đến 100");
        }

        if (before != null && before <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Con trỏ phân trang không hợp lệ");
        }

        User currentUser = userRepository.findOneByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy Channel"));

        if (!serverMemberRepository.existsByServerIdAndUserId(channel.getServerId(), currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền xem tin nhắn trong Channel này");
        }

        PageRequest pageRequest = PageRequest.of(0, effectiveLimit + 1);
        List<Message> messages = (before == null)
                ? messageRepository.findByChannelIdOrderByIdDesc(channel.getId(), pageRequest)
                : messageRepository.findByChannelIdAndIdLessThanOrderByIdDesc(channel.getId(), before, pageRequest);

        boolean hasMore = messages.size() > effectiveLimit;
        List<Message> pagedMessages = hasMore ? messages.subList(0, effectiveLimit) : messages;

        Long nextCursor = null;
        if (hasMore && !pagedMessages.isEmpty()) {
            nextCursor = pagedMessages.get(pagedMessages.size() - 1).getId();
        }

        List<MessageResponse> items = pagedMessages.stream().map(this::describeMessage).toList();

        return new CursorPageResponse<>(items, nextCursor, hasMore, effectiveLimit);
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để gửi tin nhắn");
        }

        return SecurityUtils.getCurrentUserLogin()
                .filter(login -> !login.isBlank())
                .map(login -> login.trim().toLowerCase(Locale.ENGLISH))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
    }

    private void validateRequest(Long channelId, SendMessageRequest request) {
        if (channelId == null || request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yêu cầu gửi tin nhắn không hợp lệ");
        }

        Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violations.iterator().next().getMessage());
        }
    }

    @Transactional
    public MessageResponse updateMessage(Long messageId, UpdateMessageRequest request) {
        if (messageId == null || messageId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID của tin nhắn không hợp lệ");
        }
        validateUpdateRequest(request);

        String email = getAuthenticatedEmail();
        User currentUser = userRepository.findOneByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));

        Long authorId = message.getAuthorId() != null ? message.getAuthorId() : message.getSenderId();
        if (authorId == null || !authorId.equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền sửa tin nhắn này");
        }

        lockChannelForMutation(message);
        message.setContent(request.content());
        Message savedMessage = messageRepository.saveAndFlush(message);

        MessageResponse response = describeMessage(savedMessage);
        if (savedMessage.getChannelId() != null) {
            events.publishEvent(new ChannelMessageDelivery.Updated(response));
            invalidateSaved(java.util.List.of(savedMessage.getId()));
        }
        return response;
    }

    private void validateUpdateRequest(UpdateMessageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yêu cầu sửa tin nhắn không hợp lệ");
        }

        Set<ConstraintViolation<UpdateMessageRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violations.iterator().next().getMessage());
        }
    }

    @Transactional
    public void deleteMessage(Long messageId) {
        if (messageId == null || messageId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID của tin nhắn không hợp lệ");
        }

        String email = getAuthenticatedEmail();
        User currentUser = userRepository.findOneByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getActivated()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));

        Long authorId = message.getAuthorId() != null ? message.getAuthorId() : message.getSenderId();
        if (authorId == null || !authorId.equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền xóa tin nhắn này");
        }

        lockChannelForMutation(message);
        invalidateSaved(java.util.List.of(messageId));
        messageRepository.cleanupMessages(java.util.List.of(messageId));
        messageRepository.delete(message);
        if (message.getChannelId() != null) events.publishEvent(new ChannelMessageDelivery.Deleted(message.getId(), message.getChannelId()));
    }

    private void lockChannelForMutation(Message message) {
        if (message.getChannelId() == null) return;
        Channel channel = channelRepository.findById(message.getChannelId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kênh"));
        serverRepository.findByIdForUpdate(channel.getServerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Server"));
        if (!messageRepository.existsById(message.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin nhắn không còn tồn tại");
        }
    }

    private void invalidateSaved(java.util.List<Long> ids) {
        for (User user : messageRepository.savedUsers(ids)) events.publishEvent(
                new ChannelFeaturesService.FeatureChanged(null,user.getId(),user.getEmail(),"SAVED_CHANGED"));
    }

    @Transactional(readOnly=true)
    public boolean isActivePrincipal(String principal) {
        try { resolveActiveUser(principal); return true; }
        catch (RuntimeException ex) { return false; }
    }

    public MessageResponse describeMessage(Message message) {
        var modified = message.getLastModifiedDate();
        if (modified != null && message.getCreatedDate() != null && !modified.isAfter(message.getCreatedDate())) modified = null;
        MessageResponse dto = new MessageResponse(message.getId(), message.getChannelId(), message.getAuthorId(),
                message.getContent(), message.getCreatedDate(), modified);
        if (message.getReplyToMessageId() != null) {
            messageRepository.findById(message.getReplyToMessageId())
                    .filter(target -> java.util.Objects.equals(target.getChannelId(), message.getChannelId()) && target.getConversationId() == null)
                    .ifPresent(target -> dto.setReplyTo(new MessageResponse.ReplyPreview(target.getId(), target.getAuthorId(), target.getContent())));
        }
        return dto;
    }
}
