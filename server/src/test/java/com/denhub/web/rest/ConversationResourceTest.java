package com.denhub.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.denhub.service.dto.ConversationResponseDTO;
import com.denhub.service.dto.MessageHistoryDTO;
import com.denhub.service.realtime.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ConversationResourceTest {

    private MockMvc mockMvc;

    @Mock
    private ConversationService conversationService;

    @InjectMocks
    private ConversationResource conversationResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(conversationResource).build();
    }

    @Test
    void getUserConversations_Success() throws Exception {
        ConversationResponseDTO.OtherUser otherUser = ConversationResponseDTO.OtherUser.builder()
                .userId(2L)
                .username("john")
                .name("John Doe")
                .avatar("avatar.png")
                .build();

        ConversationResponseDTO.LastMessage lastMessage = ConversationResponseDTO.LastMessage.builder()
                .messageId(10L)
                .type("TEXT")
                .content("Hello World")
                .senderId(1L)
                .createdAt(Instant.now())
                .build();

        ConversationResponseDTO conv = ConversationResponseDTO.builder()
                .conversationId(100L)
                .type("DIRECT")
                .otherUser(otherUser)
                .lastMessage(lastMessage)
                .createdDate(Instant.now())
                .build();

        when(conversationService.getUserConversations()).thenReturn(List.of(conv));

        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value(100))
                .andExpect(jsonPath("$[0].type").value("DIRECT"))
                .andExpect(jsonPath("$[0].otherUser.userId").value(2))
                .andExpect(jsonPath("$[0].otherUser.username").value("john"))
                .andExpect(jsonPath("$[0].lastMessage.content").value("Hello World"));
    }

    @Test
    void getConversationMessages_Success() throws Exception {
        MessageHistoryDTO.SenderInfo sender = MessageHistoryDTO.SenderInfo.builder()
                .userId(1L)
                .name("Sender User")
                .avatar(null)
                .build();

        MessageHistoryDTO msg = MessageHistoryDTO.builder()
                .messageId(50L)
                .conversationId(100L)
                .sender(sender)
                .type("TEXT")
                .content("Test message")
                .status("SENT")
                .createdAt(Instant.now())
                .build();

        Page<MessageHistoryDTO> page = new PageImpl<>(List.of(msg), PageRequest.of(0, 30), 1);

        when(conversationService.getConversationMessages(eq(100L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/conversations/100/messages?page=0&size=30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].messageId").value(50))
                .andExpect(jsonPath("$.content[0].conversationId").value(100))
                .andExpect(jsonPath("$.content[0].content").value("Test message"))
                .andExpect(jsonPath("$.content[0].sender.userId").value(1));
    }
}
