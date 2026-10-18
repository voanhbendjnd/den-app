package com.denhub.service;

import com.denhub.domain.Friendship;
import com.denhub.domain.User;
import com.denhub.domain.enums.FriendshipStatus;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.errors.BadRequestResourceException;
import com.denhub.service.errors.FriendReqAlreadySentException;
import com.denhub.service.errors.NotAuthorizedException;
import com.denhub.service.realtime.FriendshipAction;
import com.denhub.service.realtime.NotificationFriendshipRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationFriendshipRequest notificationFriendshipRequest;

    private FriendshipService friendshipService;
    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    private User sender;
    private User target;

    @BeforeEach
    void setUp() {
        friendshipService = new FriendshipService(
                friendshipRepository,
                userRepository,
                notificationFriendshipRequest
        );

        securityUtilsMockedStatic = mockStatic(SecurityUtils.class);

        sender = new User();
        sender.setId(1L);
        sender.setUsername("sender_user");
        sender.setName("Sender");
        sender.setAvatar("https://avatar.com/sender.png");

        target = new User();
        target.setId(2L);
        target.setUsername("target_user");
        target.setName("Target");
        target.setAvatar("https://avatar.com/target.png");
    }

    @AfterEach
    void tearDown() {
        if (securityUtilsMockedStatic != null) {
            securityUtilsMockedStatic.close();
        }
    }

    @Test
    void sendRequestAddFriend_SelfRequest_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdOrNull).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> friendshipService.sendRequestAddFriend(1L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("cannot send request for self");
    }

    @Test
    void sendRequestAddFriend_Unauthorized_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdOrNull).thenReturn(null);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> friendshipService.sendRequestAddFriend(2L))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void sendRequestAddFriend_NewRequest_Success() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdOrNull).thenReturn(1L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.empty());

        friendshipService.sendRequestAddFriend(2L);

        ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(friendshipCaptor.capture());
        Friendship savedFs = friendshipCaptor.getValue();

        assertThat(savedFs.getUserOneId()).isEqualTo(1L);
        assertThat(savedFs.getUserTwoId()).isEqualTo(2L);
        assertThat(savedFs.getStatus()).isEqualTo(FriendshipStatus.PENDING.name());
        assertThat(savedFs.getActionUserId()).isEqualTo(1L);

        ArgumentCaptor<FriendshipAction> actionCaptor = ArgumentCaptor.forClass(FriendshipAction.class);
        verify(notificationFriendshipRequest).requestReceived(actionCaptor.capture(), eq(2L));
        FriendshipAction action = actionCaptor.getValue();
        assertThat(action.getSenderId()).isEqualTo(1L);
        assertThat(action.getUsername()).isEqualTo("sender_user");
    }

    @Test
    void sendRequestAddFriend_TargetPending_AcceptsRequest() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdOrNull).thenReturn(1L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));

        Friendship existingFs = new Friendship();
        existingFs.setId(10L);
        existingFs.setUserOneId(1L);
        existingFs.setUserTwoId(2L);
        existingFs.setStatus(FriendshipStatus.PENDING.name());
        existingFs.setActionUserId(2L); // Action user was target (2L)

        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.of(existingFs));

        friendshipService.sendRequestAddFriend(2L);

        verify(friendshipRepository).save(existingFs);
        assertThat(existingFs.getStatus()).isEqualTo(FriendshipStatus.ACCEPT.name());

        verify(notificationFriendshipRequest, never()).requestAccepted(any(FriendshipAction.class), any(Long.class));
    }

    @Test
    void sendRequestAddFriend_AlreadySent_ThrowsFriendReqAlreadySentException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdOrNull).thenReturn(1L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));

        Friendship existingFs = new Friendship();
        existingFs.setUserOneId(1L);
        existingFs.setUserTwoId(2L);
        existingFs.setStatus(FriendshipStatus.PENDING.name());
        existingFs.setActionUserId(1L); // Action user was sender (1L)

        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.of(existingFs));

        assertThatThrownBy(() -> friendshipService.sendRequestAddFriend(2L))
                .isInstanceOf(FriendReqAlreadySentException.class);
    }

    @Test
    void blockUser_Unauthorized_ThrowsAccessDeniedException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(null);

        assertThatThrownBy(() -> friendshipService.blockUser(2L))
                .isInstanceOf(com.denhub.service.errors.AccessDeniedException.class);
    }

    @Test
    void blockUser_SelfBlock_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        assertThatThrownBy(() -> friendshipService.blockUser(1L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("Cannot block my self");
    }

    @Test
    void blockUser_UserNotFound_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> friendshipService.blockUser(999L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void blockUser_AlreadyBlocked_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(2L)).thenReturn(true);

        Friendship existingFs = new Friendship();
        existingFs.setUserOneId(1L);
        existingFs.setUserTwoId(2L);
        existingFs.setStatus(FriendshipStatus.BLOCKED.name());
        existingFs.setActionUserId(1L);

        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.of(existingFs));

        assertThatThrownBy(() -> friendshipService.blockUser(2L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("This user already blocked");
    }

    @Test
    void blockUser_ExistingFriendship_UpdatesStatusAndActionUser() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(2L)).thenReturn(true);

        Friendship existingFs = new Friendship();
        existingFs.setUserOneId(1L);
        existingFs.setUserTwoId(2L);
        existingFs.setStatus(FriendshipStatus.ACCEPT.name());
        existingFs.setActionUserId(2L);

        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.of(existingFs));

        friendshipService.blockUser(2L);

        assertThat(existingFs.getStatus()).isEqualTo(FriendshipStatus.BLOCKED.name());
        assertThat(existingFs.getActionUserId()).isEqualTo(1L);
        verify(friendshipRepository).save(existingFs);
    }

    @Test
    void blockUser_NoExistingFriendship_CreatesNewBlockedRecord() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.empty());

        friendshipService.blockUser(2L);

        ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(captor.capture());

        Friendship saved = captor.getValue();
        assertThat(saved.getUserOneId()).isEqualTo(1L);
        assertThat(saved.getUserTwoId()).isEqualTo(2L);
        assertThat(saved.getStatus()).isEqualTo(FriendshipStatus.BLOCKED.name());
        assertThat(saved.getActionUserId()).isEqualTo(1L);
    }

    @Test
    void removeBlock_Unauthorized_ThrowsAccessDeniedException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(null);

        assertThatThrownBy(() -> friendshipService.removeBlock(2L))
                .isInstanceOf(com.denhub.service.errors.AccessDeniedException.class);
    }

    @Test
    void removeBlock_SelfRemoveBlock_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        assertThatThrownBy(() -> friendshipService.removeBlock(1L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("Cannot remove block my self");
    }

    @Test
    void removeBlock_UserNotFound_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> friendshipService.removeBlock(999L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void removeBlock_FriendshipNotFound_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.removeBlock(2L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("Friendship not found");
    }

    @Test
    void removeBlock_NotBlockedStatus_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(2L)).thenReturn(true);

        Friendship existingFs = new Friendship();
        existingFs.setUserOneId(1L);
        existingFs.setUserTwoId(2L);
        existingFs.setStatus(FriendshipStatus.ACCEPT.name());
        existingFs.setActionUserId(1L);

        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.of(existingFs));

        assertThatThrownBy(() -> friendshipService.removeBlock(2L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("Friendship status not blocked");
    }

    @Test
    void removeBlock_NotActionUser_ThrowsException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(2L)).thenReturn(true);

        Friendship existingFs = new Friendship();
        existingFs.setUserOneId(1L);
        existingFs.setUserTwoId(2L);
        existingFs.setStatus(FriendshipStatus.BLOCKED.name());
        existingFs.setActionUserId(2L); // 2L blocked 1L, so 1L cannot unblock

        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.of(existingFs));

        assertThatThrownBy(() -> friendshipService.removeBlock(2L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("You cannot remove block for this user");
    }

    @Test
    void removeBlock_Success_DeletesRecord() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.existsById(2L)).thenReturn(true);

        Friendship existingFs = new Friendship();
        existingFs.setUserOneId(1L);
        existingFs.setUserTwoId(2L);
        existingFs.setStatus(FriendshipStatus.BLOCKED.name());
        existingFs.setActionUserId(1L); // 1L blocked 2L, so 1L can unblock

        when(friendshipRepository.findByUserOneIdAndUserTwoId(1L, 2L)).thenReturn(Optional.of(existingFs));

        friendshipService.removeBlock(2L);

        verify(friendshipRepository).delete(existingFs);
    }
}
