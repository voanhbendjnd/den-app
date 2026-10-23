package com.denhub.service.realtime;

import com.denhub.domain.Post;
import com.denhub.domain.User;
import com.denhub.domain.enums.PostStatus;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.PostRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.FileService;
import com.denhub.service.dto.DisplayPostDTO;
import com.denhub.service.errors.BadRequestResourceException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class PostFeedService {
    PostRepository postRepository;
    FileService fileService;
    FriendshipRepository friendshipRepository;
    UserRepository userRepository;
    NotificationAsyncFeedPostService notificationAsyncFeedPostService;

    @Transactional
    public DisplayPostDTO postFeed(FeedPostReq req, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadRequestResourceException("Unauthorized user", "postFeedManagement", "unauthorized");
        }

        Long userPostFeedId;
        User userPost;
        try {
            userPostFeedId = Long.parseLong(principal.getName());
            userPost = userRepository.findById(userPostFeedId)
                    .orElseThrow(() -> new BadRequestResourceException("User not found", "postManagement", "userNotFound"));
        } catch (NumberFormatException ex) {
            userPost = userRepository.findOneByEmail(principal.getName().toLowerCase())
                    .orElseThrow(() -> new BadRequestResourceException("User not found", "postManagement", "userNotFound"));
            userPostFeedId = userPost.getId();
        }

        if (!Boolean.TRUE.equals(userPost.getActivated())) {
            throw new BadRequestResourceException("User is deactivated", "postManagement", "userDeactivated");
        }

        // Handle mediaUrl & mediaUrls
        String mediaUrl = req != null ? req.getMediaUrl() : null;
        if ((mediaUrl == null || mediaUrl.trim().isEmpty())
                && req != null
                && req.getMediaUrls() != null
                && !req.getMediaUrls().isEmpty()) {
            mediaUrl = String.join(",", req.getMediaUrls());
        }
        if (mediaUrl != null) {
            mediaUrl = mediaUrl.trim();
            if (mediaUrl.isEmpty()) {
                mediaUrl = null;
            }
        }

        // Handle content
        String content = (req != null && req.getContent() != null) ? req.getContent().trim() : null;
        if (content != null && content.isEmpty()) {
            content = null;
        }

        // Validation
        if (content == null && mediaUrl == null) {
            throw new BadRequestResourceException("Post must contain text content or media", "postManagement", "emptyPost");
        }
        if (content != null && content.length() > 120) {
            throw new BadRequestResourceException("Content cannot exceed 120 characters", "postManagement", "contentTooLong");
        }
        if (mediaUrl != null && mediaUrl.length() > 1000) {
            throw new BadRequestResourceException("Media URL cannot exceed 1000 characters", "postManagement", "mediaUrlTooLong");
        }

        // Handle privacy
        String rawPrivacy = req.getPrivacy();
        String privacy = PostStatus.PUBLIC.name();
        if (rawPrivacy != null && !rawPrivacy.trim().isEmpty()) {
            try {
                privacy = PostStatus.valueOf(rawPrivacy.trim().toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                throw new BadRequestResourceException("Post status invalid format", "postManagement", "invalidformat");
            }
        }

        Post newPost = new Post();
        newPost.setContent(content);
        newPost.setUserId(userPostFeedId);
        newPost.setPrivacy(privacy);
        newPost.setLikeCounts(0);
        newPost.setMediaUrl(mediaUrl);
        newPost = postRepository.save(newPost);

        String authorDisplayName = (userPost.getName() != null && !userPost.getName().trim().isEmpty())
                ? userPost.getName()
                : userPost.getUsername();

        DisplayPostDTO payload = DisplayPostDTO.builder()
                .id(newPost.getId())
                .userId(userPost.getId())
                .authorName(authorDisplayName)
                .authorUsername(userPost.getUsername())
                .authorAvatar(userPost.getAvatar())
                .privacy(newPost.getPrivacy())
                .content(newPost.getContent())
                .likeCounts(0)
                .mediaUrl(newPost.getMediaUrl())
                .createdDate(newPost.getCreatedDate() != null ? newPost.getCreatedDate() : Instant.now())
                .build();

        if (!PostStatus.PRIVATE.name().equals(privacy)) {
            List<Long> friendIds = friendshipRepository.getAllFriendIdsByUserId(userPost.getId());
            if (friendIds != null && !friendIds.isEmpty()) {
                notificationAsyncFeedPostService.sendNotificationToFriend(friendIds, payload);
            }
        }

        return payload;
    }

    public String processSaveFiles(List<MultipartFile> files) {
        if (files != null && !files.isEmpty()) {
            try {
                List<String> saveUrls = fileService.saveAndGetUrls(files);
                return String.join(",", saveUrls);
            } catch (Exception e) {
                if (e instanceof BadRequestResourceException) {
                    throw (BadRequestResourceException) e;
                }
                throw new BadRequestResourceException("Failed to save files: " + e.getMessage(), "conversationManagement", "fileSaveFailed");
            }
        }
        return null;
    }
}

