package com.denhub.service.realtime;

import com.denhub.domain.Post;
import com.denhub.domain.User;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.PostRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.FileService;
import com.denhub.service.dto.DisplayPostDTO;
import com.denhub.service.errors.BadRequestResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostFeedServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private FileService fileService;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationAsyncFeedPostService notificationAsyncFeedPostService;

    @Mock
    private Principal principal;

    private PostFeedService postFeedService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        postFeedService = new PostFeedService(
                postRepository,
                fileService,
                friendshipRepository,
                userRepository,
                notificationAsyncFeedPostService
        );

        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setUsername("alice");
        activeUser.setName("Alice Nguyen");
        activeUser.setEmail("alice@example.com");
        activeUser.setAvatar("https://avatar.url/alice.png");
        activeUser.setActivated(true);
    }

    @Test
    void postFeed_Success_Public_BroadcastsToFriends() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("Hello world!");
        req.setPrivacy("PUBLIC");

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        when(friendshipRepository.getAllFriendIdsByUserId(1L)).thenReturn(List.of(2L, 3L));

        DisplayPostDTO result = postFeedService.postFeed(req, principal);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getAuthorName()).isEqualTo("Alice Nguyen");
        assertThat(result.getAuthorUsername()).isEqualTo("alice");
        assertThat(result.getAuthorAvatar()).isEqualTo("https://avatar.url/alice.png");
        assertThat(result.getContent()).isEqualTo("Hello world!");
        assertThat(result.getPrivacy()).isEqualTo("PUBLIC");
        assertThat(result.getLikeCounts()).isEqualTo(0);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        assertThat(savedPost.getContent()).isEqualTo("Hello world!");
        assertThat(savedPost.getLikeCounts()).isEqualTo(0);
        assertThat(savedPost.getPrivacy()).isEqualTo("PUBLIC");

        verify(notificationAsyncFeedPostService).sendNotificationToFriend(eq(List.of(2L, 3L)), any(DisplayPostDTO.class));
    }

    @Test
    void postFeed_Success_FriendsOnly_BroadcastsToFriends() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("Friends only post");
        req.setPrivacy("FRIENDS_ONLY");

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(101L);
            return p;
        });

        when(friendshipRepository.getAllFriendIdsByUserId(1L)).thenReturn(List.of(2L));

        DisplayPostDTO result = postFeedService.postFeed(req, principal);

        assertThat(result.getPrivacy()).isEqualTo("FRIENDS_ONLY");
        verify(notificationAsyncFeedPostService).sendNotificationToFriend(eq(List.of(2L)), any(DisplayPostDTO.class));
    }

    @Test
    void postFeed_Success_Private_DoesNotBroadcastToFriends() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("Secret diary");
        req.setPrivacy("PRIVATE");

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(102L);
            return p;
        });

        DisplayPostDTO result = postFeedService.postFeed(req, principal);

        assertThat(result.getPrivacy()).isEqualTo("PRIVATE");
        // Must never query friends or send WebSocket notification for PRIVATE post
        verify(friendshipRepository, never()).getAllFriendIdsByUserId(any());
        verify(notificationAsyncFeedPostService, never()).sendNotificationToFriend(any(), any());
    }

    @Test
    void postFeed_Success_FindByEmail_WhenPrincipalIsNotNumeric() {
        when(principal.getName()).thenReturn("alice@example.com");
        when(userRepository.findOneByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("Post by email principal");

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(103L);
            return p;
        });

        DisplayPostDTO result = postFeedService.postFeed(req, principal);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1L);
    }

    @Test
    void postFeed_Success_JoinsMultipleMediaUrls() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("Look at these photos");
        req.setMediaUrls(List.of("https://img.com/1.png", "https://img.com/2.png"));

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(104L);
            return p;
        });

        DisplayPostDTO result = postFeedService.postFeed(req, principal);

        assertThat(result.getMediaUrl()).isEqualTo("https://img.com/1.png,https://img.com/2.png");
    }

    @Test
    void postFeed_ThrowsUnauthorized_WhenPrincipalIsNull() {
        FeedPostReq req = new FeedPostReq();
        req.setContent("Test");

        assertThatThrownBy(() -> postFeedService.postFeed(req, null))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "unauthorized");
    }

    @Test
    void postFeed_ThrowsUserNotFound_WhenUserDoesNotExist() {
        when(principal.getName()).thenReturn("999");
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        FeedPostReq req = new FeedPostReq();
        req.setContent("Test");

        assertThatThrownBy(() -> postFeedService.postFeed(req, principal))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "userNotFound");
    }

    @Test
    void postFeed_ThrowsUserDeactivated_WhenActivatedIsFalse() {
        activeUser.setActivated(false);
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("Hello");

        assertThatThrownBy(() -> postFeedService.postFeed(req, principal))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "userDeactivated");
    }

    @Test
    void postFeed_ThrowsEmptyPost_WhenBothContentAndMediaAreNull() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("   ");
        req.setMediaUrl("");

        assertThatThrownBy(() -> postFeedService.postFeed(req, principal))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "emptyPost");
    }

    @Test
    void postFeed_ThrowsContentTooLong_WhenContentExceeds120Chars() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("a".repeat(121));

        assertThatThrownBy(() -> postFeedService.postFeed(req, principal))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "contentTooLong");
    }

    @Test
    void postFeed_ThrowsMediaUrlTooLong_WhenMediaUrlExceeds1000Chars() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("Valid content");
        req.setMediaUrl("http://img.com/" + "x".repeat(1000));

        assertThatThrownBy(() -> postFeedService.postFeed(req, principal))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "mediaUrlTooLong");
    }

    @Test
    void postFeed_ThrowsInvalidFormat_WhenPrivacyIsInvalid() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        FeedPostReq req = new FeedPostReq();
        req.setContent("Valid content");
        req.setPrivacy("INVALID_STATUS");

        assertThatThrownBy(() -> postFeedService.postFeed(req, principal))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "invalidformat");
    }
}
