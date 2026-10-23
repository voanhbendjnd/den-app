package com.denhub.service.realtime;

import com.denhub.service.dto.DisplayPostDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class NotificationAsyncFeedPostService {
    SimpMessagingTemplate simpMessagingTemplate;
    @Async
    public void sendNotificationToFriend(List<Long> friendIds, DisplayPostDTO payload){
        if (friendIds == null || friendIds.isEmpty()) {
            return;
        }
        friendIds.forEach(friendId ->{
            try{
                simpMessagingTemplate.convertAndSendToUser(friendId.toString(), "/queue/new-post", payload);
            }
            catch(Exception e){
                log.error("Error while sending notification to friends for friendId: {}", friendId.toString());
            }
        });
    }
}
