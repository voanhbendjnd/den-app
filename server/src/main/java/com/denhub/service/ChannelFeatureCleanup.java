package com.denhub.service;
import com.denhub.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
@Transactional
public class ChannelFeatureCleanup {
    private final MessageRepository messages;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    public ChannelFeatureCleanup(MessageRepository messages,UserRepository users,ApplicationEventPublisher events) {
        this.messages=messages; this.users=users; this.events=events;
    }
    public void channels(List<Long> ids) {
        if(ids.isEmpty()) return;
        for(var user:messages.savedUsersInChannels(ids)) events.publishEvent(
                new ChannelFeaturesService.FeatureChanged(null,user.getId(),user.getEmail(),"SAVED_CHANGED"));
        messages.cleanupChannels(ids);
    }
    public void membership(Long userId) {
        users.findById(userId).ifPresent(user -> events.publishEvent(
                new ChannelFeaturesService.FeatureChanged(null,user.getId(),user.getEmail(),"SAVED_CHANGED")));
    }
}
