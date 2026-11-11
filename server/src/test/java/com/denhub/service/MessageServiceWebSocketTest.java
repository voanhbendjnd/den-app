package com.denhub.service;

import com.denhub.domain.Channel;
import com.denhub.domain.Message;
import com.denhub.domain.Server;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.ServerRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceWebSocketTest {
    private static final Long CHANNEL_ID = 5L;
    private static final Long SERVER_ID = 7L;
    private static final Long USER_ID = 2L;
    private static ValidatorFactory validatorFactory;

    @Mock private MessageRepository messageRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private ServerMemberRepository serverMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher events;

    private MessageService messageService;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, channelRepository,
                serverMemberRepository, userRepository, validatorFactory.getValidator(), serverRepository, events);
    }

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setActivated(true);
        return user;
    }

    private void stubChannel() {
        Channel channel = new Channel();
        channel.setId(CHANNEL_ID);
        channel.setServerId(SERVER_ID);
        when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.of(channel));
    }

    @Test
    void sendsFromWebSocketUsingNumericPrincipalAndSavesMessage() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        stubChannel();
        when(serverRepository.findByIdForUpdate(SERVER_ID)).thenReturn(Optional.of(new Server()));
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, USER_ID)).thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(101L);
            m.setCreatedDate(Instant.parse("2026-10-10T07:30:00Z"));
            return m;
        });

        MessageResponse response = messageService.sendMessageFromWebSocket(
                CHANNEL_ID, new SendMessageRequest(" Hello "), "2");

        assertEquals(101L, response.id());
        assertEquals(CHANNEL_ID, response.channelId());
        assertEquals(USER_ID, response.senderId());
        assertEquals(" Hello ", response.content());
        verify(events).publishEvent(argThat((Object event) ->
                event instanceof com.denhub.service.realtime.ChannelMessageDelivery.Created created
                        && created.getMessage() == response));
    }

    @Test
    void sendsFromWebSocketUsingEmailPrincipalFallback() {
        when(userRepository.findOneByEmail("duy@example.com")).thenReturn(Optional.of(activeUser()));
        stubChannel();
        when(serverRepository.findByIdForUpdate(SERVER_ID)).thenReturn(Optional.of(new Server()));
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, USER_ID)).thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messageService.sendMessageFromWebSocket(
                CHANNEL_ID, new SendMessageRequest("Hi"), "DUY@example.com");

        assertEquals(USER_ID, response.senderId());
    }

    @Test
    void rejectsWebSocketSendFromNonMemberWithoutSaving() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        stubChannel();
        when(serverRepository.findByIdForUpdate(SERVER_ID)).thenReturn(Optional.of(new Server()));
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, USER_ID)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessageFromWebSocket(CHANNEL_ID, new SendMessageRequest("Hi"), "2"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(events);
    }

    @Test
    void rejectsWebSocketSendWithBlankContentOrUnknownUser() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessageFromWebSocket(CHANNEL_ID, new SendMessageRequest("   "), "2"))
                .getStatusCode());

        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessageFromWebSocket(CHANNEL_ID, new SendMessageRequest("Hi"), "99"))
                .getStatusCode());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void channelMembershipCheckAllowsMembersAndDeniesOthers() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        stubChannel();
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, USER_ID)).thenReturn(true, false);

        assertTrue(messageService.isChannelMember(CHANNEL_ID, "2"));
        assertFalse(messageService.isChannelMember(CHANNEL_ID, "2"));
        assertFalse(messageService.isChannelMember(CHANNEL_ID, null));
        assertFalse(messageService.isChannelMember(CHANNEL_ID, "404"));
    }

    @Test
    void oversizedNumericPrincipalIsUnauthorizedRatherThanServerError() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessageFromWebSocket(CHANNEL_ID,
                        new SendMessageRequest("Hi"), "9999999999999999999999999999999")).getStatusCode());
        assertFalse(messageService.isChannelMember(CHANNEL_ID, "9999999999999999999999999999999"));
        verifyNoInteractions(messageRepository, events);
    }
}

