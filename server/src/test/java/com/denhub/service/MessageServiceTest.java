package com.denhub.service;

import com.denhub.domain.Channel;
import com.denhub.domain.Message;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    private static final Long CHANNEL_ID = 5L;
    private static final Long SERVER_ID = 7L;
    private static final Long SENDER_ID = 2L;
    private static final String EMAIL = "duy@example.com";
    private static final Instant CREATED_AT = Instant.parse("2026-10-10T07:30:00Z");
    private static ValidatorFactory validatorFactory;

    @Mock private MessageRepository messageRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private ServerMemberRepository serverMemberRepository;
    @Mock private UserRepository userRepository;

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
        SecurityContextHolder.clearContext();
        messageService = new MessageService(messageRepository, channelRepository,
                serverMemberRepository, userRepository, validatorFactory.getValidator());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendsMessageUsingAuthenticatedUserAndPreservesContent() {
        authenticate("  DUY@EXAMPLE.COM  ");
        stubSender();
        stubChannel();
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(true);
        stubSavedMessage();
        String content = "  Hello mọi người!\nDòng thứ hai 🙂  ";

        MessageResponse response = messageService.sendMessage(CHANNEL_ID, new SendMessageRequest(content));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message message = messageCaptor.getValue();
        assertAll(
                () -> assertEquals(CHANNEL_ID, message.getChannelId()),
                () -> assertEquals(SENDER_ID, message.getAuthorId()),
                () -> assertEquals(content, message.getContent()),
                () -> assertEquals(new MessageResponse(101L, CHANNEL_ID, SENDER_ID, content, CREATED_AT), response));
        verify(userRepository).findOneByEmail(EMAIL);
        verify(serverMemberRepository).existsByServerIdAndUserId(SERVER_ID, SENDER_ID);
    }

    @ParameterizedTest
    @MethodSource("validBoundaryContents")
    void acceptsContentAtUtf16Limit(String content) {
        authenticate(EMAIL);
        stubSender();
        stubChannel();
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(true);
        stubSavedMessage();

        MessageResponse response = messageService.sendMessage(CHANNEL_ID, new SendMessageRequest(content));

        assertEquals(4000, content.length());
        assertEquals(content, response.content());
        verify(messageRepository).save(any(Message.class));
    }

    static Stream<String> validBoundaryContents() {
        return Stream.of("a".repeat(4000), "🙂".repeat(2000));
    }

    @ParameterizedTest
    @MethodSource("invalidContents")
    void rejectsInvalidContentBeforeAccessingRepositories(String content) {
        authenticate(EMAIL);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest(content)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(userRepository, channelRepository, serverMemberRepository, messageRepository);
    }

    static Stream<String> invalidContents() {
        return Stream.of(null, "", "   ", "\t\r\n", "\u2003", "\u00a0", "\u202f",
                "a".repeat(4001), "🙂".repeat(2000) + "a");
    }

    @Test
    void rejectsMissingRequest() {
        authenticate(EMAIL);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(CHANNEL_ID, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(userRepository, channelRepository, serverMemberRepository, messageRepository);
    }

    @Test
    void rejectsMissingChannelId() {
        authenticate(EMAIL);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(null, new SendMessageRequest("Hello")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(userRepository, channelRepository, serverMemberRepository, messageRepository);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertUnauthorizedWithoutRepositoryAccess();
    }

    @Test
    void rejectsAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertUnauthorizedWithoutRepositoryAccess();
    }

    @Test
    void rejectsUnauthenticatedPrincipal() {
        authenticate(EMAIL);
        SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);

        assertUnauthorizedWithoutRepositoryAccess();
    }

    @ParameterizedTest
    @MethodSource("invalidSubjects")
    void rejectsMissingOrBlankSubject(String subject) {
        authenticate(subject);

        assertUnauthorizedWithoutRepositoryAccess();
    }

    static Stream<String> invalidSubjects() {
        return Stream.of(null, "", "   ");
    }

    @Test
    void rejectsUserWhoNoLongerExists() {
        authenticate(EMAIL);
        when(userRepository.findOneByEmail(EMAIL)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest("Hello")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(channelRepository, serverMemberRepository, messageRepository);
    }

    @Test
    void rejectsInactiveUser() {
        authenticate(EMAIL);
        User sender = stubSender();
        sender.setActivated(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest("Hello")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(channelRepository, serverMemberRepository, messageRepository);
    }

    @Test
    void rejectsMissingChannel() {
        authenticate(EMAIL);
        stubSender();
        when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest("Hello")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(serverMemberRepository, messageRepository);
    }

    @Test
    void rejectsUserOutsideChannelServer() {
        authenticate(EMAIL);
        stubSender();
        stubChannel();
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest("Hello")));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(serverMemberRepository).existsByServerIdAndUserId(SERVER_ID, SENDER_ID);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void propagatesStorageFailureWithoutReturningSuccess() {
        authenticate(EMAIL);
        stubSender();
        stubChannel();
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(true);
        RuntimeException failure = new IllegalStateException("Simulated storage failure");
        when(messageRepository.save(any(Message.class))).thenThrow(failure);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest("Hello")));

        assertSame(failure, exception);
    }

    private void authenticate(String subject) {
        // This fixture represents an already authenticated JWT, not signature verification.
        Jwt.Builder jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("user", Map.of("id", 999L));
        if (subject != null) {
            jwt.subject(subject);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build(), List.of()));
    }

    private User stubSender() {
        User sender = new User();
        sender.setId(SENDER_ID);
        sender.setActivated(true);
        when(userRepository.findOneByEmail(EMAIL)).thenReturn(Optional.of(sender));
        return sender;
    }

    private void stubChannel() {
        Channel channel = new Channel();
        channel.setId(CHANNEL_ID);
        channel.setServerId(SERVER_ID);
        when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.of(channel));
    }

    private void stubSavedMessage() {
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(101L);
            message.setCreatedDate(CREATED_AT);
            return message;
        });
    }

    private void assertUnauthorizedWithoutRepositoryAccess() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest("Hello")));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(userRepository, channelRepository, serverMemberRepository, messageRepository);
    }
}
