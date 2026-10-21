package com.denhub.service;

import com.denhub.domain.Post;
import com.denhub.domain.User;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.PostRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.PostDTO;
import com.denhub.service.dto.ResultPaginationCursorDateId;
import com.denhub.service.errors.AccessDeniedException;
import com.denhub.service.errors.BadRequestResourceException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

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

        List<PostDTO> data = posts.stream().map(x -> {
            User author = userMap.get(x.getUserId());
            String authorName = author != null ? (author.getName() != null ? author.getName() : author.getUsername()) : "Người dùng";
            String authorUsername = author != null ? author.getUsername() : null;
            String authorAvatar = author != null ? author.getAvatar() : null;

            return PostDTO.builder()
                    .id(x.getId())
                    .userId(x.getUserId())
                    .authorName(authorName)
                    .authorUsername(authorUsername)
                    .authorAvatar(authorAvatar)
                    .privacy(x.getPrivacy())
                    .content(x.getContent())
                    .likeCounts(x.getLikeCounts() != null ? x.getLikeCounts() : 0)
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
            PostDTO last = data.getLast();
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
}
