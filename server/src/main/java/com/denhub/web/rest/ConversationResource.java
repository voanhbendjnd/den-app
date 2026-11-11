package com.denhub.web.rest;

import com.denhub.service.dto.ConversationResponseDTO;
import com.denhub.service.dto.MessageHistoryDTO;
import com.denhub.service.realtime.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Conversation", description = "Quản lý hội thoại 1-1 (Direct Message)")
@SecurityRequirement(name = "bearerAuth")
public class ConversationResource {

    ConversationService conversationService;


    @GetMapping
    @Operation(summary = "Lấy danh sách hội thoại của user hiện tại")
    public ResponseEntity<List<ConversationResponseDTO>> getUserConversations() {
        List<ConversationResponseDTO> conversations = conversationService.getUserConversations();
        return ResponseEntity.ok(conversations);
    }


    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "Lấy lịch sử tin nhắn của một hội thoại (có phân trang)")
    public ResponseEntity<Page<MessageHistoryDTO>> getConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MessageHistoryDTO> messagePage = conversationService.getConversationMessages(conversationId, pageable);
        return ResponseEntity.ok(messagePage);
    }
}
