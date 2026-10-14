package com.denhub.service.realtime;

import com.denhub.repository.UserRepository;
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
    UserRepository userRepository;

    public void requestReceived(FriendshipAction fsa, Long targetId){
        simpMessagingTemplate.convertAndSendToUser(targetId.toString(), "/queue/friend-requests/received", fsa);
        simpMessagingTemplate.convertAndSendToUser(targetId.toString(), "/topic/send-request-received", fsa);

        userRepository.findById(targetId).ifPresent(user -> {
            if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                simpMessagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/friend-requests/received", fsa);
                simpMessagingTemplate.convertAndSendToUser(user.getEmail(), "/topic/send-request-received", fsa);
            }
        });
    }

    public void requestAccepted(FriendshipAction fsa, Long targetId){
        simpMessagingTemplate.convertAndSendToUser(targetId.toString(), "/queue/friend-requests/accepted", fsa);
        simpMessagingTemplate.convertAndSendToUser(targetId.toString(), "/topic/send-request-accepted", fsa);

        userRepository.findById(targetId).ifPresent(user -> {
            if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                simpMessagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/friend-requests/accepted", fsa);
                simpMessagingTemplate.convertAndSendToUser(user.getEmail(), "/topic/send-request-accepted", fsa);
            }
        });
    }
}
