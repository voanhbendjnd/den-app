package com.denhub.service;

import com.denhub.domain.Post;
import com.denhub.domain.User;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.PostRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.domain.enums.PostStatus;
import com.denhub.service.dto.DisplayPostDTO;
import com.denhub.service.dto.ResultPaginationCursorDateId;
import com.denhub.service.dto.UpdatePostDTO;
import com.denhub.service.errors.AccessDeniedException;
import com.denhub.service.errors.BadRequestResourceException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class PostService {
    PostRepository postRepository;
    FriendshipRepository friendshipRepository;
    UserRepository userRepository;
    StringRedisTemplate stringRedisTemplate;
    public void postFeed(){

    }
    public ResultPaginationCursorDateId getAllPost(int size, String cursorDate, Long cursorId){
        Long currentUserId = SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        Instant parseCursor = null;
        try{
            if(cursorDate != null && !cursorDate.isEmpty()){
                parseCursor = Instant.parse(cursorDate);
            }
        }
        catch(Exception e){
            throw new BadRequestResourceException("Parse cursor invalid", "time", "parseInvalid");
        }

        int pageSize = size <= 0 ? 10 : size;
        List<Long> friendIds = friendshipRepository.getAllFriendIdsByUserId(currentUserId);
        if(friendIds == null){
            friendIds = Collections.emptyList();
        }
        List<Long> safeFriendIds = friendIds.isEmpty() ? List.of(-1L) : friendIds;

        Pageable pageable = PageRequest.of(0, pageSize + 1);
        Slice<Post> postSlice = postRepository.getAllPostForFeed(safeFriendIds, currentUserId, parseCursor, cursorId, pageable);

        List<Post> posts = postSlice.getContent();
        List<Long> authorIds = posts.stream().map(Post::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = authorIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(authorIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<DisplayPostDTO> data = posts.stream().map(x -> {
            User author = userMap.get(x.getUserId());
            String authorName = author != null ? (author.getName() != null ? author.getName() : author.getUsername()) : "Người dùng";
            String authorUsername = author != null ? author.getUsername() : null;
            String authorAvatar = author != null ? author.getAvatar() : null;

            boolean isLiked = false;
            if (stringRedisTemplate != null) {
                try {
                    String redisKey = String.format("post:%d:likes", x.getId());
                    isLiked = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(redisKey, String.valueOf(currentUserId)));
                } catch (Exception ignored) {
                }
            }

            return DisplayPostDTO.builder()
                    .id(x.getId())
                    .userId(x.getUserId())
                    .authorName(authorName)
                    .authorUsername(authorUsername)
                    .authorAvatar(authorAvatar)
                    .privacy(x.getPrivacy())
                    .content(x.getContent())
                    .likeCounts(x.getLikeCounts() != null ? x.getLikeCounts() : 0)
                    .isLiked(isLiked)
                    .mediaUrl(x.getMediaUrl())
                    .createdDate(x.getCreatedDate())
                    .build();
        }).collect(Collectors.toList());

        boolean hasMore = data.size() > pageSize;
        if(hasMore) {
            data.removeLast();
        }
        String nextCursorDate = null;
        Long nextCursorId = null;
        if(!data.isEmpty()) {
            DisplayPostDTO last = data.getLast();
            nextCursorDate = last.getCreatedDate() != null ? last.getCreatedDate().toString() : null;
            nextCursorId = last.getId();
        }
        ResultPaginationCursorDateId res = new ResultPaginationCursorDateId();
        res.setCursorDate(nextCursorDate);
        res.setCursorId(nextCursorId);
        res.setHasMore(hasMore);
        res.setResult(data);
        return res;
    }

    @Transactional
    public DisplayPostDTO updatePost(Long postId, UpdatePostDTO dto) {
        if (postId == null) {
            throw new BadRequestResourceException("Post ID is required", "postManagement", "postIdRequired");
        }

        Long currentUserId = SecurityUtils.getCurrentUserIdAtHolder();
        if (currentUserId == null) {
            throw new AccessDeniedException("userNotLoggedIn");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BadRequestResourceException("User not found", "userManagement", "userNotFound"));

        if (!Boolean.TRUE.equals(currentUser.getActivated())) {
            throw new BadRequestResourceException("User is deactivated", "postManagement", "userDeactivated");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BadRequestResourceException("Post not found", "postManagement", "postNotFound"));

        if (!post.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("postNotOwner");
        }

        String content = (dto != null && dto.getContent() != null) ? dto.getContent().trim() : null;
        if (content != null && content.isEmpty()) {
            content = null;
        }

        String mediaUrl = (dto != null && dto.getMediaUrl() != null) ? dto.getMediaUrl().trim() : null;
        if (mediaUrl != null && mediaUrl.isEmpty()) {
            mediaUrl = null;
        }

        if (content == null && mediaUrl == null) {
            throw new BadRequestResourceException("Post must contain text content or media", "postManagement", "emptyPost");
        }
        if (content != null && content.length() > 120) {
            throw new BadRequestResourceException("Content cannot exceed 120 characters", "postManagement", "contentTooLong");
        }
        if (mediaUrl != null && mediaUrl.length() > 1000) {
            throw new BadRequestResourceException("Media URL cannot exceed 1000 characters", "postManagement", "mediaUrlTooLong");
        }

        String rawPrivacy = dto != null ? dto.getPrivacy() : null;
        String privacy = post.getPrivacy() != null ? post.getPrivacy() : PostStatus.PUBLIC.name();
        if (rawPrivacy != null && !rawPrivacy.trim().isEmpty()) {
            try {
                privacy = PostStatus.valueOf(rawPrivacy.trim().toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                throw new BadRequestResourceException("Post status invalid format", "postManagement", "invalidformat");
            }
        }

        post.setContent(content);
        post.setPrivacy(privacy);
        post.setMediaUrl(mediaUrl);

        Post savedPost = postRepository.save(post);

        boolean isLiked = false;
        if (stringRedisTemplate != null) {
            try {
                String redisKey = String.format("post:%d:likes", savedPost.getId());
                isLiked = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(redisKey, String.valueOf(currentUserId)));
            } catch (Exception ignored) {
            }
        }

        String authorDisplayName = (currentUser.getName() != null && !currentUser.getName().trim().isEmpty())
                ? currentUser.getName()
                : currentUser.getUsername();

        return DisplayPostDTO.builder()
                .id(savedPost.getId())
                .userId(currentUser.getId())
                .authorName(authorDisplayName)
                .authorUsername(currentUser.getUsername())
                .authorAvatar(currentUser.getAvatar())
                .privacy(savedPost.getPrivacy())
                .content(savedPost.getContent())
                .likeCounts(savedPost.getLikeCounts() != null ? savedPost.getLikeCounts() : 0)
                .isLiked(isLiked)
                .mediaUrl(savedPost.getMediaUrl())
                .createdDate(savedPost.getCreatedDate() != null ? savedPost.getCreatedDate() : Instant.now())
                .build();
    }

    @Transactional
    public void deletePost(Long postId) {
        if (postId == null) {
            throw new BadRequestResourceException("Post ID is required", "postManagement", "postIdRequired");
        }

        Long currentUserId = SecurityUtils.getCurrentUserIdAtHolder();
        if (currentUserId == null) {
            throw new AccessDeniedException("userNotLoggedIn");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BadRequestResourceException("User not found", "userManagement", "userNotFound"));

        if (!Boolean.TRUE.equals(currentUser.getActivated())) {
            throw new BadRequestResourceException("User is deactivated", "postManagement", "userDeactivated");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BadRequestResourceException("Post not found", "postManagement", "postNotFound"));

        if (!post.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("postNotOwner");
        }

        postRepository.delete(post);

        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.delete(String.format("post:%d:likes", postId));
            } catch (Exception ignored) {
            }
        }
    }
}
