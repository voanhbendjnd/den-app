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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class LikeService {
    StringRedisTemplate stringRedisTemplate;
    UserRepository userRepository;
    PostRepository postRepository;
    FriendshipRepository friendshipRepository;

    static String POST_LIKES_KEY_PREFIX = "post:%d:likes";

    @Transactional
    public LikeResponseDTO toggleLike(Long postId) {
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
            throw new BadRequestResourceException("Account not activated", "userManagement", "notactivated");
        }

        // 1. Kiểm tra bài viết có tồn tại trong database
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BadRequestResourceException("Post not found", "postManagement", "postNotFound"));

        // 2. Kiểm tra quyền like bài viết theo privacy
        String privacy = post.getPrivacy() != null ? post.getPrivacy().toUpperCase() : "PUBLIC";
        boolean isAuthor = post.getUserId().equals(currentUserId);

        if (!isAuthor) {
            if ("PRIVATE".equals(privacy)) {
                throw new AccessDeniedException("privatePost");
            } else if ("FRIENDS_ONLY".equals(privacy)) {
                List<Long> friendIds = friendshipRepository.getAllFriendIdsByUserId(post.getUserId());
                if (friendIds == null || !friendIds.contains(currentUserId)) {
                    throw new AccessDeniedException("friendsOnlyPost");
                }
            }
        }

        // 3. Tương tác với Redis Set
        String redisKey = String.format(POST_LIKES_KEY_PREFIX, postId);
        String userStrId = String.valueOf(currentUserId);
        Boolean isAlreadyLiked = stringRedisTemplate.opsForSet().isMember(redisKey, userStrId);

        boolean currentLikedState;
        if (Boolean.TRUE.equals(isAlreadyLiked)) {
            stringRedisTemplate.opsForSet().remove(redisKey, userStrId);
            currentLikedState = false;
        } else {
            stringRedisTemplate.opsForSet().add(redisKey, userStrId);
            currentLikedState = true;
        }

        Long totalLikes = stringRedisTemplate.opsForSet().size(redisKey);
        long safeTotalLikes = totalLikes != null ? totalLikes : 0;

        // 4. Đồng bộ số like vào SQL Server Database
        post.setLikeCounts((int) safeTotalLikes);
        postRepository.save(post);

        return LikeResponseDTO.builder()
                .liked(currentLikedState)
                .totalLikes(safeTotalLikes)
                .build();
    }
}

