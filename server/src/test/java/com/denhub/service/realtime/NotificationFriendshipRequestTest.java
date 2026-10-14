package com.denhub.service.realtime;

import com.denhub.domain.User;
import com.denhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationFriendshipRequestTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationFriendshipRequest notificationFriendshipRequest;

    private FriendshipAction action;
    private User recipient;

    @BeforeEach
    void setUp() {
        action = FriendshipAction.builder()
                .senderId(1L)
                .username("sender_bob")
                .name("Bob Sender")
                .status("PENDING")
                .event("SEND_FRIEND_REQUEST_RECEIVED")
                .build();

        recipient = new User();
        recipient.setId(2L);
        recipient.setEmail("alice@denhub.com");
    }

    @Test
    @DisplayName("requestReceived should send to both targetId as String and target email")
    void requestReceived_DualDelivery_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));

        notificationFriendshipRequest.requestReceived(action, 2L);

        verify(simpMessagingTemplate).convertAndSendToUser("2", "/queue/friend-requests/received", action);
        verify(simpMessagingTemplate).convertAndSendToUser("2", "/topic/send-request-received", action);
        verify(simpMessagingTemplate).convertAndSendToUser("alice@denhub.com", "/queue/friend-requests/received", action);
        verify(simpMessagingTemplate).convertAndSendToUser("alice@denhub.com", "/topic/send-request-received", action);
    }

    @Test
    @DisplayName("requestAccepted should send to both targetId as String and target email")
    void requestAccepted_DualDelivery_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));

        notificationFriendshipRequest.requestAccepted(action, 2L);

        verify(simpMessagingTemplate).convertAndSendToUser("2", "/queue/friend-requests/accepted", action);
        verify(simpMessagingTemplate).convertAndSendToUser("2", "/topic/send-request-accepted", action);
        verify(simpMessagingTemplate).convertAndSendToUser("alice@denhub.com", "/queue/friend-requests/accepted", action);
        verify(simpMessagingTemplate).convertAndSendToUser("alice@denhub.com", "/topic/send-request-accepted", action);
    }

    @Test
    @DisplayName("requestReceived should still send to targetId even if user not found in repository")
    void requestReceived_UserNotFound_StillSendsToTargetId() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        notificationFriendshipRequest.requestReceived(action, 99L);

        verify(simpMessagingTemplate).convertAndSendToUser("99", "/queue/friend-requests/received", action);
        verify(simpMessagingTemplate).convertAndSendToUser("99", "/topic/send-request-received", action);
        verifyNoMoreInteractions(simpMessagingTemplate);
    }
}
