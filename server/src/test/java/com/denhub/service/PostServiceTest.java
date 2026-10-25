package com.denhub.service;

import com.denhub.domain.Post;
import com.denhub.domain.User;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.PostRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.DisplayPostDTO;
import com.denhub.service.dto.ResultPaginationCursorDateId;
import com.denhub.service.dto.UpdatePostDTO;
import com.denhub.service.errors.AccessDeniedException;
import com.denhub.service.errors.BadRequestResourceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private PostService postService;
    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, friendshipRepository, userRepository, stringRedisTemplate);
        securityUtilsMockedStatic = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMockedStatic.close();
    }

    @Test
    void getAllPost_Unauthorized_ThrowsAccessDeniedException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(null);

        assertThatThrownBy(() -> postService.getAllPost(10, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAllPost_InvalidCursorDate_ThrowsBadRequestResourceException() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        assertThatThrownBy(() -> postService.getAllPost(10, "invalid-date", null))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("Parse cursor invalid");
    }

    @Test
    void getAllPost_NoFriends_ReturnsFeedSuccessfully() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(friendshipRepository.getAllFriendIdsByUserId(1L)).thenReturn(Collections.emptyList());

        Slice<Post> emptySlice = new SliceImpl<>(Collections.emptyList());
        when(postRepository.getAllPostForFeed(eq(List.of(-1L)), eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptySlice);

        ResultPaginationCursorDateId result = postService.getAllPost(10, null, null);

        assertThat(result).isNotNull();
        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getCursorDate()).isNull();
        assertThat(result.getCursorId()).isNull();
        assertThat((List<?>) result.getResult()).isEmpty();
    }

    @Test
    void getAllPost_WithFriendsAndPosts_MapsAuthorsAndReturnsResults() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(friendshipRepository.getAllFriendIdsByUserId(1L)).thenReturn(List.of(2L));

        Instant now = Instant.now();
        Post post1 = new Post();
        post1.setId(101L);
        post1.setUserId(2L);
        post1.setContent("Friend post content");
        post1.setPrivacy("PUBLIC");
        post1.setLikeCounts(5);
        post1.setCreatedDate(now);

        User friend = new User();
        friend.setId(2L);
        friend.setName("Alice");
        friend.setUsername("alice123");
        friend.setAvatar("https://example.com/avatar.jpg");

        List<Post> posts = new ArrayList<>(List.of(post1));
        Slice<Post> slice = new SliceImpl<>(posts);

        when(postRepository.getAllPostForFeed(eq(List.of(2L)), eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(slice);
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(friend));

        ResultPaginationCursorDateId result = postService.getAllPost(10, null, null);

        assertThat(result).isNotNull();
        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getCursorDate()).isEqualTo(now.toString());
        assertThat(result.getCursorId()).isEqualTo(101L);

        @SuppressWarnings("unchecked")
        List<DisplayPostDTO> data = (List<DisplayPostDTO>) result.getResult();
        assertThat(data).hasSize(1);
        DisplayPostDTO dto = data.getFirst();
        assertThat(dto.getId()).isEqualTo(101L);
        assertThat(dto.getUserId()).isEqualTo(2L);
        assertThat(dto.getAuthorName()).isEqualTo("Alice");
        assertThat(dto.getAuthorUsername()).isEqualTo("alice123");
        assertThat(dto.getAuthorAvatar()).isEqualTo("https://example.com/avatar.jpg");
        assertThat(dto.getContent()).isEqualTo("Friend post content");
        assertThat(dto.getLikeCounts()).isEqualTo(5);
    }

    @Test
    void getAllPost_HasMorePosts_TrimsToPageSizeAndSetsHasMoreTrue() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);
        when(friendshipRepository.getAllFriendIdsByUserId(1L)).thenReturn(List.of(2L));

        Instant now = Instant.now();
        Post post1 = new Post();
        post1.setId(102L);
        post1.setUserId(2L);
        post1.setContent("Post 1");
        post1.setPrivacy("FRIENDS_ONLY");
        post1.setCreatedDate(now);

        Post post2 = new Post();
        post2.setId(101L);
        post2.setUserId(2L);
        post2.setContent("Post 2");
        post2.setPrivacy("PUBLIC");
        post2.setCreatedDate(now.minusSeconds(60));

        // pageSize is 1, so 2 posts returned by repo means hasMore = true
        List<Post> posts = new ArrayList<>(List.of(post1, post2));
        Slice<Post> slice = new SliceImpl<>(posts);

        when(postRepository.getAllPostForFeed(eq(List.of(2L)), eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(slice);
        when(userRepository.findAllById(List.of(2L))).thenReturn(Collections.emptyList());

        ResultPaginationCursorDateId result = postService.getAllPost(1, null, null);

        assertThat(result.isHasMore()).isTrue();

        @SuppressWarnings("unchecked")
        List<DisplayPostDTO> data = (List<DisplayPostDTO>) result.getResult();
        assertThat(data).hasSize(1);
        assertThat(data.getFirst().getId()).isEqualTo(102L);
        assertThat(result.getCursorDate()).isEqualTo(now.toString());
        assertThat(result.getCursorId()).isEqualTo(102L);
    }

    @Test
    void updatePost_Success() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        User author = new User();
        author.setId(1L);
        author.setUsername("john");
        author.setName("John Doe");
        author.setActivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        Post post = new Post();
        post.setId(100L);
        post.setUserId(1L);
        post.setContent("Old content");
        post.setPrivacy("PUBLIC");
        post.setMediaUrl("https://img.com/old.png");
        post.setCreatedDate(Instant.now());
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePostDTO updateDTO = UpdatePostDTO.builder()
                .content("Updated content")
                .privacy("FRIENDS_ONLY")
                .mediaUrl("https://img.com/new.png")
                .build();

        DisplayPostDTO result = postService.updatePost(100L, updateDTO);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getContent()).isEqualTo("Updated content");
        assertThat(result.getPrivacy()).isEqualTo("FRIENDS_ONLY");
        assertThat(result.getMediaUrl()).isEqualTo("https://img.com/new.png");
        assertThat(result.getAuthorName()).isEqualTo("John Doe");
        verify(postRepository).save(post);
    }

    @Test
    void updatePost_ThrowsBadRequest_WhenPostNotFound() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        User author = new User();
        author.setId(1L);
        author.setActivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        UpdatePostDTO dto = UpdatePostDTO.builder().content("New content").build();

        assertThatThrownBy(() -> postService.updatePost(999L, dto))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "postNotFound");
    }

    @Test
    void updatePost_ThrowsAccessDenied_WhenNotOwner() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        User caller = new User();
        caller.setId(1L);
        caller.setActivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));

        Post post = new Post();
        post.setId(100L);
        post.setUserId(2L); // Different user
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        UpdatePostDTO dto = UpdatePostDTO.builder().content("Hacked content").build();

        assertThatThrownBy(() -> postService.updatePost(100L, dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasFieldOrPropertyWithValue("entityName", "postNotOwner");
    }

    @Test
    void updatePost_ThrowsBadRequest_WhenEmptyPost() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        User author = new User();
        author.setId(1L);
        author.setActivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        Post post = new Post();
        post.setId(100L);
        post.setUserId(1L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        UpdatePostDTO dto = UpdatePostDTO.builder().content("   ").mediaUrl("").build();

        assertThatThrownBy(() -> postService.updatePost(100L, dto))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "emptyPost");
    }

    @Test
    void updatePost_ThrowsBadRequest_WhenContentTooLong() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        User author = new User();
        author.setId(1L);
        author.setActivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        Post post = new Post();
        post.setId(100L);
        post.setUserId(1L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        UpdatePostDTO dto = UpdatePostDTO.builder().content("a".repeat(121)).build();

        assertThatThrownBy(() -> postService.updatePost(100L, dto))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "contentTooLong");
    }

    @Test
    void deletePost_Success() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        User author = new User();
        author.setId(1L);
        author.setActivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        Post post = new Post();
        post.setId(100L);
        post.setUserId(1L);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        postService.deletePost(100L);

        verify(postRepository).delete(post);
        verify(stringRedisTemplate).delete("post:100:likes");
    }

    @Test
    void deletePost_ThrowsBadRequest_WhenPostNotFound() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        User author = new User();
        author.setId(1L);
        author.setActivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(999L))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "postNotFound");
    }

    @Test
    void deletePost_ThrowsAccessDenied_WhenNotOwner() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(1L);

        User caller = new User();
        caller.setId(1L);
        caller.setActivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));

        Post post = new Post();
        post.setId(100L);
        post.setUserId(2L); // Different user
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(100L))
                .isInstanceOf(AccessDeniedException.class)
                .hasFieldOrPropertyWithValue("entityName", "postNotOwner");
    }

    @Test
    void deletePost_ThrowsAccessDenied_WhenNotLoggedIn() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserIdAtHolder).thenReturn(null);

        assertThatThrownBy(() -> postService.deletePost(100L))
                .isInstanceOf(AccessDeniedException.class)
                .hasFieldOrPropertyWithValue("entityName", "userNotLoggedIn");
    }
}
