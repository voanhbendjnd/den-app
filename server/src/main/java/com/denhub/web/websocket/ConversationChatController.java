package com.denhub.web.websocket;

import com.denhub.service.realtime.SendMessageReq;
import com.denhub.service.realtime.SendMessageRes;
import com.denhub.service.realtime.ConversationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Destination: /app/conversations/send
 */
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationChatController {

    ConversationService conversationService;

    @MessageMapping("/conversations/send")
    public void sendDirectMessage(@Payload SendMessageReq req, Principal principal) {
        conversationService.processAndSendMessage(req, principal);
    }
}
