package com.denhub.service;

import com.denhub.domain.Channel;
import com.denhub.domain.Message;
import com.denhub.domain.User;
import com.denhub.repository.ChannelRepository;
import com.denhub.repository.MessageRepository;
import com.denhub.repository.ServerMemberRepository;
import com.denhub.repository.UserRepository;
import com.denhub.service.dto.CursorPageResponse;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import com.denhub.service.dto.UpdateMessageRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.springframework.data.domain.PageRequest;
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
    @Mock private com.denhub.repository.ServerRepository serverRepository;

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
                serverMemberRepository, userRepository, validatorFactory.getValidator(), serverRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesToSendWhenServerWasDeletedAfterChannelWasRead() {
        authenticate(EMAIL);
        stubSender();
        stubChannel(false);
        when(serverRepository.findByIdForUpdate(SERVER_ID)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest("Hello"))).getStatusCode());
        verifyNoInteractions(messageRepository, serverMemberRepository);
    }

    @Test
    void sendsMessageUsingAuthenticatedUserAndPreservesContent() {
        authenticate("  DUY@EXAMPLE.COM  ");
        stubSender();
        stubChannel(true);
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
        stubChannel(true);
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
        stubChannel(true);
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
        stubChannel(true);
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(true);
        RuntimeException failure = new IllegalStateException("Simulated storage failure");
        when(messageRepository.save(any(Message.class))).thenThrow(failure);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> messageService.sendMessage(CHANNEL_ID, new SendMessageRequest("Hello")));

        assertSame(failure, exception);
    }

    @Test
    void getMessages_Success_DefaultLimitWithoutBefore() {
        authenticate(EMAIL);
        stubSender();
        stubChannel(false);
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(true);

        Message msg1 = createMockMessage(102L, "Tin nhắn mới nhất");
        Message msg2 = createMockMessage(101L, "Tin nhắn cũ hơn");
        when(messageRepository.findByChannelIdOrderByIdDesc(eq(CHANNEL_ID), any(PageRequest.class)))
                .thenReturn(List.of(msg1, msg2));

        CursorPageResponse<MessageResponse> response = messageService.getMessages(CHANNEL_ID, null, null);

        assertNotNull(response);
        assertEquals(2, response.items().size());
        assertEquals(102L, response.items().get(0).id());
        assertEquals("Tin nhắn mới nhất", response.items().get(0).content());
        assertEquals(101L, response.items().get(1).id());
        assertEquals(30, response.limit());
        assertFalse(response.hasMore());
        assertNull(response.nextCursor());
        verify(messageRepository).findByChannelIdOrderByIdDesc(eq(CHANNEL_ID), any(PageRequest.class));
    }

    @Test
    void getMessages_Success_WithBeforeAndLimit_HasMore() {
        authenticate(EMAIL);
        stubSender();
        stubChannel(false);
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(true);

        Message msg103 = createMockMessage(103L, "Msg 103");
        Message msg102 = createMockMessage(102L, "Msg 102");
        Message msg101 = createMockMessage(101L, "Msg 101");
        when(messageRepository.findByChannelIdAndIdLessThanOrderByIdDesc(eq(CHANNEL_ID), eq(104L), any(PageRequest.class)))
                .thenReturn(List.of(msg103, msg102, msg101));

        CursorPageResponse<MessageResponse> response = messageService.getMessages(CHANNEL_ID, 104L, 2);

        assertNotNull(response);
        assertEquals(2, response.items().size());
        assertEquals(103L, response.items().get(0).id());
        assertEquals(102L, response.items().get(1).id());
        assertTrue(response.hasMore());
        assertEquals(102L, response.nextCursor());
        assertEquals(2, response.limit());
    }

    @Test
    void getMessages_Success_EmptyChannel() {
        authenticate(EMAIL);
        stubSender();
        stubChannel(false);
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(true);
        when(messageRepository.findByChannelIdOrderByIdDesc(eq(CHANNEL_ID), any(PageRequest.class)))
                .thenReturn(List.of());

        CursorPageResponse<MessageResponse> response = messageService.getMessages(CHANNEL_ID, null, 10);

        assertNotNull(response);
        assertTrue(response.items().isEmpty());
        assertFalse(response.hasMore());
        assertNull(response.nextCursor());
        assertEquals(10, response.limit());
    }

    @Test
    void getMessages_BadRequest_WhenChannelIdInvalid() {
        authenticate(EMAIL);
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(null, null, 30));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(0L, null, 30));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());

        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(-1L, null, 30));
        assertEquals(HttpStatus.BAD_REQUEST, ex3.getStatusCode());
    }

    @Test
    void getMessages_BadRequest_WhenLimitOutOfRange() {
        authenticate(EMAIL);
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(CHANNEL_ID, null, 0));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(CHANNEL_ID, null, 101));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    void getMessages_BadRequest_WhenBeforeInvalid() {
        authenticate(EMAIL);
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(CHANNEL_ID, 0L, 30));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(CHANNEL_ID, -10L, 30));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    void getMessages_Unauthorized_WhenNotAuthenticated() {
        SecurityContextHolder.clearContext();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(CHANNEL_ID, null, 30));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(messageRepository, channelRepository);
    }

    @Test
    void getMessages_Unauthorized_WhenUserInactive() {
        authenticate(EMAIL);
        User sender = new User();
        sender.setId(SENDER_ID);
        sender.setActivated(false);
        when(userRepository.findOneByEmail(EMAIL)).thenReturn(Optional.of(sender));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(CHANNEL_ID, null, 30));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(messageRepository, channelRepository);
    }

    @Test
    void getMessages_NotFound_WhenChannelNotFound() {
        authenticate(EMAIL);
        stubSender();
        when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(CHANNEL_ID, null, 30));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(messageRepository, serverMemberRepository);
    }

    @Test
    void getMessages_Forbidden_WhenNotServerMember() {
        authenticate(EMAIL);
        stubSender();
        stubChannel(false);
        when(serverMemberRepository.existsByServerIdAndUserId(SERVER_ID, SENDER_ID)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessages(CHANNEL_ID, null, 30));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(messageRepository);
    }

    private Message createMockMessage(Long id, String content) {
        Message message = new Message();
        message.setId(id);
        message.setChannelId(CHANNEL_ID);
        message.setAuthorId(SENDER_ID);
        message.setContent(content);
        message.setCreatedDate(CREATED_AT);
        return message;
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

    private void stubChannel(boolean writing) {
        if (writing) when(serverRepository.findByIdForUpdate(SERVER_ID)).thenReturn(Optional.of(new com.denhub.domain.Server()));
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

    @Test
    void updateMessage_Success_WhenAuthor() {
        authenticate(EMAIL);
        stubSender();

        Message message = new Message();
        message.setId(101L);
        message.setChannelId(CHANNEL_ID);
        message.setAuthorId(SENDER_ID);
        message.setContent("Old content");
        message.setCreatedDate(CREATED_AT);

        when(messageRepository.findById(101L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageResponse response = messageService.updateMessage(101L, new UpdateMessageRequest("New edited content"));

        assertNotNull(response);
        assertEquals(101L, response.id());
        assertEquals("New edited content", response.content());
        assertEquals(CHANNEL_ID, response.channelId());
        assertEquals(SENDER_ID, response.senderId());
        assertEquals(CREATED_AT, response.createdAt());
        verify(messageRepository).save(message);
    }

    @Test
    void updateMessage_Fail_WhenInvalidId() {
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(null, new UpdateMessageRequest("Valid text")));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(0L, new UpdateMessageRequest("Valid text")));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());

        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(-5L, new UpdateMessageRequest("Valid text")));
        assertEquals(HttpStatus.BAD_REQUEST, ex3.getStatusCode());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void updateMessage_Fail_WhenBlankContent() {
        assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(101L, null));

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(101L, new UpdateMessageRequest(null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(101L, new UpdateMessageRequest("")));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());

        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(101L, new UpdateMessageRequest("   \t\n  ")));
        assertEquals(HttpStatus.BAD_REQUEST, ex3.getStatusCode());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void updateMessage_Fail_WhenContentTooLong() {
        String longContent = "a".repeat(4001);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(101L, new UpdateMessageRequest(longContent)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void updateMessage_Fail_WhenNotLoggedIn() {
        SecurityContextHolder.clearContext();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(101L, new UpdateMessageRequest("Valid text")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void updateMessage_Fail_WhenUserNotFoundOrInactive() {
        authenticate(EMAIL);
        when(userRepository.findOneByEmail(EMAIL)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(101L, new UpdateMessageRequest("Valid text")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void updateMessage_Fail_WhenMessageNotFound() {
        authenticate(EMAIL);
        stubSender();
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(999L, new UpdateMessageRequest("Valid text")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void updateMessage_Fail_WhenNotAuthor() {
        authenticate(EMAIL);
        stubSender(); // ID is SENDER_ID (2L)

        Message message = new Message();
        message.setId(101L);
        message.setChannelId(CHANNEL_ID);
        message.setAuthorId(999L); // Different author!
        message.setContent("Original text");

        when(messageRepository.findById(101L)).thenReturn(Optional.of(message));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(101L, new UpdateMessageRequest("Attempted edit")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Bạn không có quyền sửa tin nhắn này"));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void updateMessage_Fail_WhenServerOwnerNotAuthor() {
        // Enforces BR-MSG-02: Even if user is Owner/Admin, only author can edit
        authenticate(EMAIL);
        stubSender(); // User ID 2L

        Message otherUsersMessage = new Message();
        otherUsersMessage.setId(102L);
        otherUsersMessage.setChannelId(CHANNEL_ID);
        otherUsersMessage.setAuthorId(888L); // Author is 888L
        otherUsersMessage.setContent("Member's message");

        when(messageRepository.findById(102L)).thenReturn(Optional.of(otherUsersMessage));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.updateMessage(102L, new UpdateMessageRequest("Owner trying to edit member's message")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(messageRepository, never()).save(any());
    }
}
