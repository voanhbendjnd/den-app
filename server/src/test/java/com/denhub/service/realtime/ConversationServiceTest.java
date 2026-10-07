package com.denhub.service.realtime;

import com.denhub.domain.Conversation;
import com.denhub.domain.Message;
import com.denhub.domain.User;
import com.denhub.domain.enums.ConversationType;
import com.denhub.domain.enums.MessageType;
import com.denhub.repository.ConversationMemberRepository;
import com.denhub.repository.ConversationRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.errors.BadRequestResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private Principal principal;

    private ConversationService conversationService;

    private User senderUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository,
                conversationMemberRepository,
                messageRepository,
                userRepository,
                simpMessagingTemplate
        );

        senderUser = new User();
        senderUser.setId(1L);
        senderUser.setName("Sender User");
        senderUser.setEmail("sender@denhub.com");

        targetUser = new User();
        targetUser.setId(2L);
        targetUser.setName("Target User");
        targetUser.setEmail("target@denhub.com");
    }

    @Test
    void processAndSendMessage_Text_CreateNewConversation_Success() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(conversationRepository.findDirectConversationBetweenUsers(ConversationType.DIRECT.name(), 1L, 2L))
                .thenReturn(Optional.empty());

        Conversation savedConversation = new Conversation();
        savedConversation.setId(100L);
        savedConversation.setType(ConversationType.DIRECT.name());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(savedConversation);

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(500L);
            return m;
        });

        SendMessageReq req = SendMessageReq.builder()
                .targetUserId(2L)
                .type("TEXT")
                .content("Hello World")
                .build();

        SendMessageRes res = conversationService.processAndSendMessage(req, principal);

        assertThat(res).isNotNull();
        assertThat(res.getMessageId()).isEqualTo(500L);
        assertThat(res.getConversationId()).isEqualTo(100L);
        assertThat(res.getContent()).isEqualTo("Hello World");
        assertThat(res.getType()).isEqualTo("TEXT");
        assertThat(res.getSender().getUserId()).isEqualTo(1L);

        verify(simpMessagingTemplate).convertAndSend(eq("/topic/conversations/100"), any(SendMessageRes.class));
    }

    @Test
    void processAndSendMessage_ImageWithMetadata_Success() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));

        Conversation conversation = new Conversation();
        conversation.setId(100L);
        conversation.setType(ConversationType.DIRECT.name());
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(true);

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(501L);
            return m;
        });

        SendMessageReq.Metadata meta = SendMessageReq.Metadata.builder()
                .fileName("sample.png")
                .fileSize(1024L)
                .width(800)
                .height(600)
                .build();

        SendMessageReq req = SendMessageReq.builder()
                .conversationId(100L)
                .type("IMAGE")
                .content("Image caption")
                .mediaUrl("https://storage.denhub.com/sample.png")
                .metadata(meta)
                .build();

        SendMessageRes res = conversationService.processAndSendMessage(req, principal);

        assertThat(res).isNotNull();
        assertThat(res.getMessageId()).isEqualTo(501L);
        assertThat(res.getType()).isEqualTo("IMAGE");
        assertThat(res.getMediaUrl()).isEqualTo("https://storage.denhub.com/sample.png");
        assertThat(res.getMetadata()).isNotNull();
        assertThat(res.getMetadata().getFileName()).isEqualTo("sample.png");
        assertThat(res.getMetadata().getWidth()).isEqualTo(800);
        assertThat(res.getMetadata().getHeight()).isEqualTo(600);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getMediaUrl()).isEqualTo("https://storage.denhub.com/sample.png");
        assertThat(savedMessage.getWidth()).isEqualTo(800);

        verify(simpMessagingTemplate).convertAndSend(eq("/topic/conversations/100"), any(SendMessageRes.class));
    }

    @Test
    void processAndSendMessage_NotMemberOfConversation_ThrowsException() {
        when(principal.getName()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));

        Conversation conversation = new Conversation();
        conversation.setId(100L);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(false);

        SendMessageReq req = SendMessageReq.builder()
                .conversationId(100L)
                .content("Hello")
                .build();

        assertThatThrownBy(() -> conversationService.processAndSendMessage(req, principal))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("not member");
    }

    @Test
    void processAndSendMessage_NullPrincipal_ThrowsException() {
        SendMessageReq req = SendMessageReq.builder().content("Hello").build();
        assertThatThrownBy(() -> conversationService.processAndSendMessage(req, null))
                .isInstanceOf(BadRequestResourceException.class)
                .hasMessageContaining("Unauthorized user");
    }
}
