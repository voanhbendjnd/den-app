package com.denhub.web.rest;

import com.denhub.service.MessageService;
import com.denhub.service.dto.CursorPageResponse;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import com.denhub.service.dto.UpdateMessageRequest;
import com.denhub.util.ResFormatResponse;
import com.denhub.web.rest.errors.ExceptionTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.zalando.problem.jackson.ProblemModule;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MessageResourceIT {

    private static final Long CHANNEL_ID = 5L;
    private static final Long SENDER_ID = 2L;
    private static final String CONTENT = "Hello mọi người!";

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageResource messageResource;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new ProblemModule())
                .registerModule(new ConstraintViolationProblemModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(messageResource)
                .setMessageConverters(converter)
                .setControllerAdvice(new ResFormatResponse(), new ExceptionTranslator())
                .build();
    }

    @Test
    void sendMessageSuccessReturns201Created() throws Exception {
        MessageResponse response = new MessageResponse(
                101L, CHANNEL_ID, SENDER_ID, CONTENT, Instant.parse("2026-10-10T07:30:00Z"));

        when(messageService.sendMessage(eq(CHANNEL_ID), any(SendMessageRequest.class))).thenReturn(response);

        SendMessageRequest request = new SendMessageRequest(CONTENT);

        mockMvc.perform(post("/api/channels/{channelId}/messages", CHANNEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.message").value("Gửi tin nhắn thành công"))
                .andExpect(jsonPath("$.error").hasJsonPath())
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.channelId").value(CHANNEL_ID))
                .andExpect(jsonPath("$.data.senderId").value(SENDER_ID))
                .andExpect(jsonPath("$.data.content").value(CONTENT))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-10T07:30:00Z"));

        verify(messageService).sendMessage(eq(CHANNEL_ID), any(SendMessageRequest.class));
    }

    @Test
    void sendMessageWithBlankContentReturns400BadRequest() throws Exception {
        SendMessageRequest request = new SendMessageRequest("");

        mockMvc.perform(post("/api/channels/{channelId}/messages", CHANNEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService);
    }

    @Test
    void sendMessageExceedingMaxCharsReturns400BadRequest() throws Exception {
        SendMessageRequest request = new SendMessageRequest("a".repeat(4001));

        mockMvc.perform(post("/api/channels/{channelId}/messages", CHANNEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService);
    }

    @Test
    void sendMessageWhenServiceThrowsUnauthorizedReturns401() throws Exception {
        when(messageService.sendMessage(eq(CHANNEL_ID), any(SendMessageRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để gửi tin nhắn"));

        SendMessageRequest request = new SendMessageRequest(CONTENT);

        mockMvc.perform(post("/api/channels/{channelId}/messages", CHANNEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessageWhenServiceThrowsForbiddenReturns403() throws Exception {
        when(messageService.sendMessage(eq(CHANNEL_ID), any(SendMessageRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền gửi tin nhắn trong Channel này"));

        SendMessageRequest request = new SendMessageRequest(CONTENT);

        mockMvc.perform(post("/api/channels/{channelId}/messages", CHANNEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendMessageWhenChannelNotFoundReturns404() throws Exception {
        when(messageService.sendMessage(eq(9999L), any(SendMessageRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Channel"));

        SendMessageRequest request = new SendMessageRequest(CONTENT);

        mockMvc.perform(post("/api/channels/{channelId}/messages", 9999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("404 NOT_FOUND \"Không tìm thấy Channel\""))
                .andExpect(jsonPath("$.message").value("error.http.404"));
    }

    @Test
    void databaseErrorDoesNotExposeSqlOrNestedCause() throws Exception {
        when(messageService.sendMessage(eq(CHANNEL_ID), any(SendMessageRequest.class)))
                .thenThrow(new DataIntegrityViolationException("insert into messages: internal SQL",
                        new IllegalStateException("internal database connection details")));

        mockMvc.perform(post("/api/channels/{channelId}/messages", CHANNEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendMessageRequest(CONTENT))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("Đã xảy ra lỗi hệ thống"))
                .andExpect(jsonPath("$.message").value("error.http.500"))
                .andExpect(jsonPath("$.path").value("/api/channels/5/messages"))
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getMessagesSuccessReturns200OkWithCursorPageResponse() throws Exception {
        MessageResponse msg1 = new MessageResponse(102L, CHANNEL_ID, SENDER_ID, "Msg 102", Instant.parse("2026-10-10T08:00:00Z"));
        MessageResponse msg2 = new MessageResponse(101L, CHANNEL_ID, SENDER_ID, "Msg 101", Instant.parse("2026-10-10T07:30:00Z"));
        CursorPageResponse<MessageResponse> pageResponse = new CursorPageResponse<>(
                List.of(msg1, msg2), 101L, true, 2);

        when(messageService.getMessages(eq(CHANNEL_ID), eq(105L), eq(2))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/channels/{channelId}/messages", CHANNEL_ID)
                        .param("before", "105")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Lấy lịch sử tin nhắn thành công"))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").value(102))
                .andExpect(jsonPath("$.data.items[0].content").value("Msg 102"))
                .andExpect(jsonPath("$.data.items[1].id").value(101))
                .andExpect(jsonPath("$.data.items[1].content").value("Msg 101"))
                .andExpect(jsonPath("$.data.nextCursor").value(101))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.limit").value(2));

        verify(messageService).getMessages(eq(CHANNEL_ID), eq(105L), eq(2));
    }

    @Test
    void getMessagesWhenChannelNotFoundReturns404() throws Exception {
        when(messageService.getMessages(eq(9999L), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Channel"));

        mockMvc.perform(get("/api/channels/{channelId}/messages", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("404 NOT_FOUND \"Không tìm thấy Channel\""));
    }

    @Test
    void getMessagesWhenForbiddenReturns403() throws Exception {
        when(messageService.getMessages(eq(CHANNEL_ID), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem tin nhắn trong Channel này"));

        mockMvc.perform(get("/api/channels/{channelId}/messages", CHANNEL_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("403 FORBIDDEN \"Bạn không có quyền xem tin nhắn trong Channel này\""));
    }

    @Test
    void getMessagesWhenBadRequestReturns400() throws Exception {
        when(messageService.getMessages(eq(CHANNEL_ID), any(), eq(150)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng tin nhắn phải từ 1 đến 100"));

        mockMvc.perform(get("/api/channels/{channelId}/messages", CHANNEL_ID)
                        .param("limit", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("400 BAD_REQUEST \"Số lượng tin nhắn phải từ 1 đến 100\""));
    }

    @Test
    void updateMessageSuccessReturns200Ok() throws Exception {
        Long messageId = 101L;
        String updatedContent = "Nội dung sau khi sửa";
        Instant now = Instant.parse("2026-10-10T08:00:00Z");
        MessageResponse response = new MessageResponse(
                messageId, CHANNEL_ID, SENDER_ID, updatedContent, Instant.parse("2026-10-10T07:30:00Z"), now);

        when(messageService.updateMessage(eq(messageId), any(UpdateMessageRequest.class))).thenReturn(response);

        UpdateMessageRequest request = new UpdateMessageRequest(updatedContent);

        mockMvc.perform(patch("/api/messages/{messageId}", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Chỉnh sửa tin nhắn thành công"))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.data.id").value(messageId))
                .andExpect(jsonPath("$.data.content").value(updatedContent))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-10-10T08:00:00Z"));

        verify(messageService).updateMessage(eq(messageId), any(UpdateMessageRequest.class));
    }

    @Test
    void updateMessageViaPutSuccessReturns200Ok() throws Exception {
        Long messageId = 101L;
        String updatedContent = "Nội dung sau khi sửa qua PUT";
        MessageResponse response = new MessageResponse(
                messageId, CHANNEL_ID, SENDER_ID, updatedContent, Instant.parse("2026-10-10T07:30:00Z"), Instant.now());

        when(messageService.updateMessage(eq(messageId), any(UpdateMessageRequest.class))).thenReturn(response);

        UpdateMessageRequest request = new UpdateMessageRequest(updatedContent);

        mockMvc.perform(put("/api/messages/{messageId}", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Chỉnh sửa tin nhắn thành công"))
                .andExpect(jsonPath("$.data.content").value(updatedContent));

        verify(messageService).updateMessage(eq(messageId), any(UpdateMessageRequest.class));
    }

    @Test
    void updateMessageWithBlankContentReturns400BadRequest() throws Exception {
        Long messageId = 101L;
        UpdateMessageRequest request = new UpdateMessageRequest("   ");

        mockMvc.perform(patch("/api/messages/{messageId}", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService);
    }

    @Test
    void updateMessageWhenNotFoundReturns404() throws Exception {
        Long messageId = 9999L;
        when(messageService.updateMessage(eq(messageId), any(UpdateMessageRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));

        UpdateMessageRequest request = new UpdateMessageRequest("Nội dung mới");

        mockMvc.perform(patch("/api/messages/{messageId}", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("404 NOT_FOUND \"Không tìm thấy tin nhắn\""));
    }

    @Test
    void updateMessageWhenForbiddenReturns403() throws Exception {
        Long messageId = 101L;
        when(messageService.updateMessage(eq(messageId), any(UpdateMessageRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền sửa tin nhắn này"));

        UpdateMessageRequest request = new UpdateMessageRequest("Nội dung mới");

        mockMvc.perform(patch("/api/messages/{messageId}", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("403 FORBIDDEN \"Bạn không có quyền sửa tin nhắn này\""));
    }
}
