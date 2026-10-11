package com.denhub.web.rest;

import com.denhub.service.MessageService;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(status().isNotFound());
    }
}
