package com.denhub.web.websocket;

import com.denhub.domain.enums.PostStatus;
import com.denhub.service.errors.BadRequestResourceException;
import com.denhub.service.realtime.FeedPostReq;
import com.denhub.service.realtime.PostFeedService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class PostFeedController {
    PostFeedService postFeedService;

    private static void checkValidStatus(String reqStatus) {
        if (reqStatus == null || reqStatus.trim().isEmpty()) {
            return;
        }
        try {
            PostStatus.valueOf(reqStatus.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestResourceException("Post status invalid format", "postManagement", "invalidformat");
        }
    }

    @MessageMapping("/post")
    public void postFeed(@Payload @Valid FeedPostReq req, Principal principal) {
        if (req != null) {
            checkValidStatus(req.getPrivacy());
        }
        postFeedService.postFeed(req, principal);
    }
}

