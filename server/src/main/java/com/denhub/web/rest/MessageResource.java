package com.denhub.web.rest;

import com.denhub.service.MessageService;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import com.denhub.util.anotation.ApiMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Message", description = "API quản lý tin nhắn trong Channel")
@SecurityRequirement(name = "bearerAuth")
public class MessageResource {

    private final MessageService messageService;

    public MessageResource(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/channels/{channelId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Gửi tin nhắn thành công")
    @Operation(
        summary = "Gửi tin nhắn vào Channel",
        description = "Gửi tin nhắn văn bản mới vào Channel trong Server. Người gửi lấy từ JWT đã xác thực. Yêu cầu người dùng là thành viên của Server."
    )
    public ResponseEntity<MessageResponse> sendMessage(
            @Parameter(description = "ID của Channel", example = "1")
            @PathVariable("channelId") Long channelId,
            @Valid @RequestBody SendMessageRequest request) {
        MessageResponse response = messageService.sendMessage(channelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
