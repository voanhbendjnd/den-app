package com.denhub.service;

import com.denhub.domain.Post;
import com.denhub.domain.User;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.PostRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.LikeResponseDTO;
import com.denhub.service.errors.AccessDeniedException;
import com.denhub.service.errors.BadRequestResourceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    private LikeService likeService;
    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    private User currentUser;
    private Post publicPost;

    @BeforeEach
    void setUp() {
        likeService = new LikeService(
                stringRedisTemplate,
                userRepository,
                postRepository,
                friendshipRepository
        );

        securityUtilsMockedStatic = mockStatic(SecurityUtils.class);

        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("alice");
        currentUser.setActivated(true);

        publicPost = new Post();
        publicPost.setId(100L);
        publicPost.setUserId(2L);
        publicPost.setContent("Public post");
        publicPost.setPrivacy("PUBLIC");
        publicPost.setLikeCounts(0);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMockedStatic.close();
    }

    @Test
    void toggleLike_Success_WhenNotYetLiked_AddsLikeAndSyncsDatabase() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(postRepository.findById(100L)).thenReturn(Optional.of(publicPost));

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("post:100:likes", "1")).thenReturn(false);
        when(setOperations.size("post:100:likes")).thenReturn(1L);

        LikeResponseDTO result = likeService.toggleLike(100L);

        assertThat(result).isNotNull();
        assertThat(result.isLiked()).isTrue();
        assertThat(result.getTotalLikes()).isEqualTo(1L);

        verify(setOperations).add("post:100:likes", "1");
        verify(postRepository).save(publicPost);
        assertThat(publicPost.getLikeCounts()).isEqualTo(1);
    }

    @Test
    void toggleLike_Success_WhenAlreadyLiked_RemovesLikeAndSyncsDatabase() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(postRepository.findById(100L)).thenReturn(Optional.of(publicPost));

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("post:100:likes", "1")).thenReturn(true);
        when(setOperations.size("post:100:likes")).thenReturn(0L);

        LikeResponseDTO result = likeService.toggleLike(100L);

        assertThat(result).isNotNull();
        assertThat(result.isLiked()).isFalse();
        assertThat(result.getTotalLikes()).isEqualTo(0L);

        verify(setOperations).remove("post:100:likes", "1");
        verify(postRepository).save(publicPost);
        assertThat(publicPost.getLikeCounts()).isEqualTo(0);
    }

    @Test
    void toggleLike_Success_AuthorCanLikeOwnPrivatePost() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        Post ownPrivatePost = new Post();
        ownPrivatePost.setId(101L);
        ownPrivatePost.setUserId(1L);
        ownPrivatePost.setPrivacy("PRIVATE");

        when(postRepository.findById(101L)).thenReturn(Optional.of(ownPrivatePost));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("post:101:likes", "1")).thenReturn(false);
        when(setOperations.size("post:101:likes")).thenReturn(1L);

        LikeResponseDTO result = likeService.toggleLike(101L);

        assertThat(result.isLiked()).isTrue();
        verify(postRepository).save(ownPrivatePost);
    }

    @Test
    void toggleLike_ThrowsAccessDenied_WhenPrivatePostAndNotAuthor() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        Post privatePost = new Post();
        privatePost.setId(102L);
        privatePost.setUserId(2L);
        privatePost.setPrivacy("PRIVATE");

        when(postRepository.findById(102L)).thenReturn(Optional.of(privatePost));

        assertThatThrownBy(() -> likeService.toggleLike(102L))
                .isInstanceOf(AccessDeniedException.class)
                .hasFieldOrPropertyWithValue("entityName", "privatePost");
    }

    @Test
    void toggleLike_ThrowsAccessDenied_WhenFriendsOnlyAndNotFriend() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        Post friendsOnlyPost = new Post();
        friendsOnlyPost.setId(103L);
        friendsOnlyPost.setUserId(2L);
        friendsOnlyPost.setPrivacy("FRIENDS_ONLY");

        when(postRepository.findById(103L)).thenReturn(Optional.of(friendsOnlyPost));
        when(friendshipRepository.getAllFriendIdsByUserId(2L)).thenReturn(List.of(3L, 4L));

        assertThatThrownBy(() -> likeService.toggleLike(103L))
                .isInstanceOf(AccessDeniedException.class)
                .hasFieldOrPropertyWithValue("entityName", "friendsOnlyPost");
    }

    @Test
    void toggleLike_Success_WhenFriendsOnlyAndIsFriend() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        Post friendsOnlyPost = new Post();
        friendsOnlyPost.setId(103L);
        friendsOnlyPost.setUserId(2L);
        friendsOnlyPost.setPrivacy("FRIENDS_ONLY");

        when(postRepository.findById(103L)).thenReturn(Optional.of(friendsOnlyPost));
        when(friendshipRepository.getAllFriendIdsByUserId(2L)).thenReturn(List.of(1L, 4L));

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("post:103:likes", "1")).thenReturn(false);
        when(setOperations.size("post:103:likes")).thenReturn(1L);

        LikeResponseDTO result = likeService.toggleLike(103L);

        assertThat(result.isLiked()).isTrue();
        verify(postRepository).save(friendsOnlyPost);
    }

    @Test
    void toggleLike_ThrowsBadRequest_WhenPostNotFound() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> likeService.toggleLike(999L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "postNotFound");
    }

    @Test
    void toggleLike_ThrowsAccessDenied_WhenNotLoggedIn() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(null);

        assertThatThrownBy(() -> likeService.toggleLike(100L))
                .isInstanceOf(AccessDeniedException.class)
                .hasFieldOrPropertyWithValue("entityName", "userNotLoggedIn");
    }

    @Test
    void toggleLike_ThrowsBadRequest_WhenUserDeactivated() {
        currentUser.setActivated(false);
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        assertThatThrownBy(() -> likeService.toggleLike(100L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "notactivated");
    }
}
