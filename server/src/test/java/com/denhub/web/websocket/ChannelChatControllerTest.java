package com.denhub.web.websocket;

import com.denhub.service.MessageService;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.handler.annotation.support.DestinationVariableMethodArgumentResolver;
import org.springframework.messaging.support.MessageBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelChatControllerTest {

    @Mock private MessageService messageService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private ChannelChatController controller;

    private final Principal principal = () -> "2";

    @Test
    void resolvesChannelAndSendsWhenCompiledWithoutParameterNames() throws Exception {
        // Reproduce the IDE build: retain the real controller bytecode and annotations,
        // but remove MethodParameters, which Gradle normally adds with -parameters.
        ClassReader reader;
        try (var bytes = ChannelChatController.class.getResourceAsStream("ChannelChatController.class")) {
            assertNotNull(bytes);
            reader = new ClassReader(bytes);
        }
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitParameter(String name, int access) {
                        // Deliberately omit parameter-name metadata.
                    }
                };
            }
        }, 0);
        byte[] compiled = writer.toByteArray();
        Class<?> ideController = new ClassLoader(ChannelChatController.class.getClassLoader()) {
            Class<?> defineController() {
                return defineClass(ChannelChatController.class.getName(), compiled, 0, compiled.length);
            }
        }.defineController();
        var method = ideController.getMethod("sendChannelMessage", Long.class, SendMessageRequest.class,
                Principal.class, String.class, String.class);
        assertFalse(method.getParameters()[0].isNamePresent());
        MethodParameter channel = new MethodParameter(method, 0);
        channel.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        var resolver = new DestinationVariableMethodArgumentResolver(new DefaultConversionService());
        var frame = MessageBuilder.withPayload("{\"content\":\"Hello\"}")
                .setHeader(DestinationVariableMethodArgumentResolver.DESTINATION_TEMPLATE_VARIABLES_HEADER,
                        Map.of("channelId", "5"))
                .build();
        Object resolvedChannel = resolver.resolveArgument(channel, frame);
        assertEquals(5L, resolvedChannel);

        SendMessageRequest request = new SendMessageRequest("Hello");
        MessageResponse saved = new MessageResponse(101L, 5L, 2L, "Hello", Instant.now());
        when(messageService.sendMessageFromWebSocket(5L, request, "2")).thenReturn(saved);
        Object instance = ideController.getConstructor(MessageService.class, SimpMessagingTemplate.class)
                .newInstance(messageService, messagingTemplate);
        method.invoke(instance, resolvedChannel, request, principal, "ide-session", "ide-request");
        Map<?, ?> result = captureResult("ide-session");
        assertEquals(201, result.get("statusCode"));
        assertSame(saved, result.get("message"));
        assertEquals("ide-request", result.get("requestId"));
    }

    @Test
    void acknowledgesCommittedMessageOnlyToOriginatingSessionWithoutSecondBroadcast() {
        SendMessageRequest request = new SendMessageRequest("Hello");
        MessageResponse saved = new MessageResponse(101L, 5L, 2L, "Hello", Instant.now());
        when(messageService.sendMessageFromWebSocket(5L, request, "2")).thenReturn(saved);

        controller.sendChannelMessage(5L, request, principal, "tab-a", "request-1");

        Map<?, ?> result = captureResult("tab-a");
        assertEquals("request-1", result.get("requestId"));
        assertEquals(5L, result.get("channelId"));
        assertEquals(201, result.get("statusCode"));
        assertSame(saved, result.get("message"));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void returnsSafeFailureWhenServiceRejects() {
        SendMessageRequest request = new SendMessageRequest("Hello");
        when(messageService.sendMessageFromWebSocket(5L, request, "2"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "private internal detail"));

        controller.sendChannelMessage(5L, request, principal, "tab-b", "request-2");

        Map<?, ?> result = captureResult("tab-b");
        assertEquals(403, result.get("statusCode"));
        assertEquals("request-2", result.get("requestId"));
        assertFalse(result.get("detail").toString().contains("private internal"));
        assertNull(result.get("message"));
    }

    @Test
    void ignoresMessageWithoutPrincipal() {
        controller.sendChannelMessage(5L, new SendMessageRequest("Hello"), null, "tab-c", "request-3");

        verifyNoInteractions(messageService);
        assertEquals(401, captureResult("tab-c").get("statusCode"));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void missingRequestIdDoesNotSaveMessage() {
        controller.sendChannelMessage(5L, new SendMessageRequest("Hello"), principal, "tab-a", null);
        assertEquals(400, captureResult("tab-a").get("statusCode"));
        verifyNoInteractions(messageService);
    }

    @Test
    void transactionFailureDoesNotAcknowledgeSuccessOrExposeException() {
        when(messageService.sendMessageFromWebSocket(anyLong(), any(), anyString()))
                .thenThrow(new IllegalStateException("sensitive SQL connection detail"));
        controller.sendChannelMessage(5L, new SendMessageRequest("Hello"), principal, "tab-a", "request-4");
        Map<?, ?> result = captureResult("tab-a");
        assertEquals(500, result.get("statusCode"));
        assertNull(result.get("message"));
        assertFalse(result.toString().contains("sensitive"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404})
    void returnsCorrelatedValidationAndPermissionFailures(int status) {
        when(messageService.sendMessageFromWebSocket(anyLong(), any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.valueOf(status), "internal"));
        controller.sendChannelMessage(5L, new SendMessageRequest("Hello"), principal, "tab-a", "request-5");
        Map<?, ?> result = captureResult("tab-a");
        assertEquals(status, result.get("statusCode"));
        assertEquals("request-5", result.get("requestId"));
    }

    @Test
    void malformedPayloadReturnsCorrelatedBadRequest() {
        controller.handleException(new MessageConversionException("raw content"), "tab-a", "request-6");
        assertEquals(400, captureResult("tab-a").get("statusCode"));
        verifyNoInteractions(messageService);
    }

    private Map<?, ?> captureResult(String sessionId) {
        ArgumentCaptor<Object> result = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Map<String, Object>> headers = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(eq(sessionId), eq("/queue/channel-message-results"),
                result.capture(), headers.capture());
        assertEquals(sessionId, headers.getValue().get("simpSessionId"));
        return (Map<?, ?>) result.getValue();
    }
}

