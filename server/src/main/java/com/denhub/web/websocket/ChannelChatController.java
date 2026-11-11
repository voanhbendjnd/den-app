package com.denhub.web.websocket;

import com.denhub.service.MessageService;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Destination gửi: /app/channels/{channelId}/send
 * Destination phát sóng: /topic/channels/{channelId}
 */
@Controller
public class ChannelChatController {

    private static final Logger log = LoggerFactory.getLogger(ChannelChatController.class);

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChannelChatController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/channels/{channelId}/send")
    public void sendChannelMessage(@DestinationVariable Long channelId,
                                   @Payload SendMessageRequest request,
                                   Principal principal,
                                   @Header("simpSessionId") String sessionId,
                                   @Header(name = "request-id", required = false) String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,100}")) {
            reply(sessionId, requestId, channelId, 400, "Yêu cầu gửi tin nhắn không hợp lệ", null);
            return;
        }
        if (principal == null) {
            reply(sessionId, requestId, channelId, 401, safeDetail(401), null);
            return;
        }
        try {
            MessageResponse saved = messageService.sendMessageFromWebSocket(channelId, request, principal.getName());
            // The proxied service returns only after its transaction has committed.
            reply(sessionId, requestId, channelId, 201, null, saved);
        } catch (ResponseStatusException ex) {
            int status = ex.getStatusCode().value();
            reply(sessionId, requestId, channelId, status, safeDetail(status), null);
        } catch (RuntimeException ex) {
            log.warn("Channel send failed, channelId={}, exception={}", channelId, ex.getClass().getSimpleName());
            reply(sessionId, requestId, channelId, 500, safeDetail(500), null);
        }
    }

    @MessageExceptionHandler
    public void handleException(Exception ex,
                                @Header("simpSessionId") String sessionId,
                                @Header(name = "request-id", required = false) String requestId) {
        int status = ex instanceof MessageConversionException ? 400 : 500;
        reply(sessionId, requestId, null, status, safeDetail(status), null);
    }

    private String safeDetail(int status) {
        return switch (status) {
            case 400 -> "Nội dung hoặc yêu cầu gửi tin nhắn không hợp lệ";
            case 401 -> "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại";
            case 403 -> "Bạn không có quyền gửi tin nhắn trong Channel này";
            case 404 -> "Không tìm thấy Channel hoặc Server";
            default -> "Không thể xác nhận gửi tin nhắn. Hãy kiểm tra lịch sử trước khi thử lại";
        };
    }

    private void reply(String sessionId, String requestId, Long channelId, int status,
                       String detail, MessageResponse message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("channelId", channelId);
        result.put("statusCode", status);
        result.put("message", message);
        result.put("detail", detail);
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setLeaveMutable(true);
        try {
            // Address the originating session, not every tab with the same user principal.
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/channel-message-results", result,
                    headers.getMessageHeaders());
        } catch (RuntimeException ex) {
            log.warn("Could not deliver channel send result, status={}", status);
        }
    }
}

