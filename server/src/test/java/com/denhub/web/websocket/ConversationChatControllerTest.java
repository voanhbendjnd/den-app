package com.denhub.web.websocket;

import com.denhub.service.realtime.SendMessageReq;
import com.denhub.service.realtime.SendMessageRes;
import com.denhub.service.realtime.ConversationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationChatControllerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private Principal principal;

    @InjectMocks
    private ConversationChatController controller;

    @Test
    void sendDirectMessage_DelegatesToService() {
        SendMessageReq req = SendMessageReq.builder()
                .conversationId(10L)
                .content("Hello World")
                .type("TEXT")
                .build();

        SendMessageRes expectedRes = SendMessageRes.builder()
                .messageId(100L)
                .conversationId(10L)
                .content("Hello World")
                .type("TEXT")
                .status("SENT")
                .build();

        controller.sendDirectMessage(req, principal);

        verify(conversationService).processAndSendMessage(req, principal);
    }
}
