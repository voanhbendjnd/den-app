package com.denhub.web.websocket;

import com.denhub.service.errors.BadRequestResourceException;
import com.denhub.service.realtime.FeedPostReq;
import com.denhub.service.realtime.PostFeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostFeedControllerTest {

    @Mock
    private PostFeedService postFeedService;

    @Mock
    private Principal principal;

    private PostFeedController postFeedController;

    @BeforeEach
    void setUp() {
        postFeedController = new PostFeedController(postFeedService);
    }

    @Test
    void postFeed_CallsService_WhenValid() {
        FeedPostReq req = new FeedPostReq();
        req.setContent("Hello world");
        req.setPrivacy("PUBLIC");

        postFeedController.postFeed(req, principal);

        verify(postFeedService).postFeed(req, principal);
    }

    @Test
    void postFeed_AcceptsNullPrivacy() {
        FeedPostReq req = new FeedPostReq();
        req.setContent("Hello world");
        req.setPrivacy(null);

        postFeedController.postFeed(req, principal);

        verify(postFeedService).postFeed(req, principal);
    }

    @Test
    void postFeed_ThrowsInvalidFormat_WhenPrivacyIsInvalid() {
        FeedPostReq req = new FeedPostReq();
        req.setContent("Hello world");
        req.setPrivacy("INVALID_TYPE");

        assertThatThrownBy(() -> postFeedController.postFeed(req, principal))
                .isInstanceOf(BadRequestResourceException.class)
                .hasFieldOrPropertyWithValue("errorKey", "invalidformat");
    }
}
