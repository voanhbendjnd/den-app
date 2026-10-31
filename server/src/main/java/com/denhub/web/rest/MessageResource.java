package com.denhub.web.rest;

import com.denhub.service.MessageService;
import com.denhub.service.dto.CursorPageResponse;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import com.denhub.service.dto.UpdateMessageRequest;
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

    @GetMapping("/channels/{channelId}/messages")
    @ApiMessage("Lấy lịch sử tin nhắn thành công")
    @Operation(
        summary = "Lấy lịch sử tin nhắn trong Channel",
        description = "Lấy danh sách tin nhắn phân trang bằng con trỏ (Cursor Pagination) sắp xếp theo ID giảm dần. Yêu cầu là thành viên của Server chứa Channel."
    )
    public ResponseEntity<CursorPageResponse<MessageResponse>> getMessages(
            @Parameter(description = "ID của Channel", example = "1")
            @PathVariable("channelId") Long channelId,
            @Parameter(description = "ID con trỏ (lấy các tin nhắn có ID nhỏ hơn con trỏ này)", example = "105")
            @RequestParam(name = "before", required = false) Long before,
            @Parameter(description = "Số lượng tin nhắn cần lấy (1 đến 100, mặc định 30)", example = "30")
            @RequestParam(name = "limit", required = false, defaultValue = "30") Integer limit) {
        CursorPageResponse<MessageResponse> response = messageService.getMessages(channelId, before, limit);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/messages/{messageId}")
    @ApiMessage("Chỉnh sửa tin nhắn thành công")
    @Operation(
        summary = "Chỉnh sửa tin nhắn",
        description = "Chỉnh sửa nội dung tin nhắn văn bản đã gửi. Chỉ duy nhất tác giả tin nhắn mới có quyền chỉnh sửa (BR-MSG-02)."
    )
    public ResponseEntity<MessageResponse> updateMessage(
            @Parameter(description = "ID của tin nhắn", example = "101")
            @PathVariable("messageId") Long messageId,
            @Valid @RequestBody UpdateMessageRequest request) {
        MessageResponse response = messageService.updateMessage(messageId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/messages/{messageId}")
    @ApiMessage("Chỉnh sửa tin nhắn thành công")
    @Operation(
        summary = "Chỉnh sửa tin nhắn (PUT)",
        description = "Chỉnh sửa nội dung tin nhắn văn bản đã gửi (hỗ trợ HTTP PUT fallback)."
    )
    public ResponseEntity<MessageResponse> updateMessagePut(
            @Parameter(description = "ID của tin nhắn", example = "101")
            @PathVariable("messageId") Long messageId,
            @Valid @RequestBody UpdateMessageRequest request) {
        MessageResponse response = messageService.updateMessage(messageId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Xóa tin nhắn trong Channel",
        description = "Xóa tin nhắn vĩnh viễn khỏi Channel. Yêu cầu người thực hiện là chính tác giả tin nhắn."
    )
    public ResponseEntity<Void> deleteMessage(
            @Parameter(description = "ID của tin nhắn", example = "101")
            @PathVariable("messageId") Long messageId) {
        messageService.deleteMessage(messageId);
        return ResponseEntity.noContent().build();
    }
}

