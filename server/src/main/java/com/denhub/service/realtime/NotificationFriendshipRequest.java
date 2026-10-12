package com.denhub.service.realtime;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class NotificationFriendshipRequest {
    SimpMessagingTemplate simpMessagingTemplate;


    public void requestReceived(FriendshipAction fsa, Long targetId){
        simpMessagingTemplate.convertAndSendToUser(targetId.toString(), "/queue/friend-requests/received", fsa);
        simpMessagingTemplate.convertAndSendToUser(targetId.toString(), "/topic/send-request-received", fsa);
    }

    public void requestAccepted(FriendshipAction fsa, Long targetId){
        simpMessagingTemplate.convertAndSendToUser(targetId.toString(), "/queue/friend-requests/accepted", fsa);
        simpMessagingTemplate.convertAndSendToUser(targetId.toString(), "/topic/send-request-accepted", fsa);
    }
}
