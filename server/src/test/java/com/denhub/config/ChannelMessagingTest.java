package com.denhub.config;

import com.denhub.security.SecurityUtils;
import com.denhub.service.MessageService;
import com.denhub.service.dto.MessageResponse;
import com.denhub.service.realtime.ChannelMessageDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.DefaultUserDestinationResolver;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transaction/event synchronization; interceptors exercise actual STOMP/SIMP headers. */
@SpringJUnitConfig(ChannelMessagingTest.TestConfig.class)
class ChannelMessagingTest {
    // Registered explicitly by @SpringJUnitConfig; no component stereotype that an IDE app launch can scan.
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class TestConfig {
        @Bean SimpMessagingTemplate messaging() { return mock(SimpMessagingTemplate.class); }
        @Bean ChannelMessageDelivery delivery(SimpMessagingTemplate messaging) {
            return new ChannelMessageDelivery(messaging);
        }
        @Bean PlatformTransactionManager transactions() {
            return new DataSourceTransactionManager(new DriverManagerDataSource(
                    "jdbc:h2:mem:channel-message-events;DB_CLOSE_DELAY=-1", "sa", ""));
        }
    }

    @Autowired ApplicationEventPublisher events;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired PlatformTransactionManager transactions;
    private MessageService service;
    private JwtDecoder decoder;
    private WebSocketConfig config;
    private ChannelInterceptor inbound;
    private ChannelInterceptor outbound;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        reset(messaging);
        service = mock(MessageService.class);
        decoder = mock(JwtDecoder.class);
        config = new WebSocketConfig(mock(SecurityUtils.class), events, decoder, service);
        Registration input = new Registration();
        Registration output = new Registration();
        config.configureClientInboundChannel(input);
        config.configureClientOutboundChannel(output);
        inbound = input.interceptors().getFirst();
        outbound = output.interceptors().getFirst();
    }

    @Test
    void editsAndDeletesBroadcastOnlyAfterCommit() {
        MessageResponse updated = saved();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            events.publishEvent(new ChannelMessageDelivery.Updated(updated));
            events.publishEvent(new ChannelMessageDelivery.Deleted(101L, 5L));
            verifyNoInteractions(messaging);
        });
        verify(messaging).convertAndSend("/topic/channels/5", updated);
        verify(messaging).convertAndSend("/topic/channels/5",
                Map.of("eventType", "DELETED", "id", 101L, "channelId", 5L));
    }

    @Test
    void rollbackDoesNotBroadcastEditsOrDeletes() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            events.publishEvent(new ChannelMessageDelivery.Updated(saved()));
            events.publishEvent(new ChannelMessageDelivery.Deleted(101L, 5L));
            tx.setRollbackOnly();
        });
        verifyNoInteractions(messaging);
    }

    @Test
    void featureEventsArePrivateOrChannelScopedAfterCommit() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            events.publishEvent(new com.denhub.service.ChannelFeaturesService.FeatureChanged(5L,null,null,"PINS_CHANGED"));
            events.publishEvent(new com.denhub.service.ChannelFeaturesService.FeatureChanged(null,2L,"member@example.test","SAVED_CHANGED"));
            verifyNoInteractions(messaging);
        });
        verify(messaging).convertAndSend("/topic/channels/5",Map.of("channelId",5L,"eventType","PINS_CHANGED"));
        var payload=new java.util.LinkedHashMap<String,Object>(); payload.put("channelId",null); payload.put("eventType","SAVED_CHANGED");
        verify(messaging).convertAndSendToUser("2","/queue/channel-features",payload);
        verify(messaging).convertAndSendToUser("member@example.test","/queue/channel-features",payload);
    }

    @Test
    void rollbackDoesNotSendPrivatePreferencesOrPins() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            events.publishEvent(new com.denhub.service.ChannelFeaturesService.FeatureChanged(5L,null,null,"PINS_CHANGED"));
            events.publishEvent(new com.denhub.service.ChannelFeaturesService.FeatureChanged(5L,2L,"member@example.test","PREFERENCE_CHANGED"));
            tx.setRollbackOnly();
        });
        verifyNoInteractions(messaging);
    }

    @Test
    void privateFeatureQueueRejectsPhysicalAndOtherUserSubscriptions() {
        connect("features-session",Instant.now().plusSeconds(60));
        when(service.isActivePrincipal("2")).thenReturn(true);
        assertNotNull(inbound.preSend(stomp(StompCommand.SUBSCRIBE,"features-session","/user/queue/channel-features"),channel));
        for(String destination:List.of("/queue/channel-features-user-features-session","/user/other/queue/channel-features")) {
            assertThrows(MessageDeliveryException.class,() -> inbound.preSend(stomp(StompCommand.SUBSCRIBE,"features-session",destination),channel));
        }
        when(service.isActivePrincipal("2")).thenReturn(false);
        assertNull(outbound.preSend(delivery("features-session","/queue/channel-features-user-features-session"),channel));
    }

    @Test
    void broadcastsExactlyOnceOnlyAfterCommit() {
        MessageResponse message = saved();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            events.publishEvent(new ChannelMessageDelivery.Created(message));
            verifyNoInteractions(messaging);
        });
        verify(messaging, times(1)).convertAndSend("/topic/channels/5", message);
        verifyNoMoreInteractions(messaging);
    }

    @Test
    void rollbackNeverBroadcasts() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            events.publishEvent(new ChannelMessageDelivery.Created(saved()));
            tx.setRollbackOnly();
        });
        verifyNoInteractions(messaging);
    }

    @Test
    void missingTransactionDoesNotPublishUncommittedMessages() {
        events.publishEvent(new ChannelMessageDelivery.Created(saved()));
        verifyNoInteractions(messaging);
    }

    @Test
    void brokerFailureAfterCommitDoesNotReportDatabaseSendFailure() {
        doThrow(new IllegalStateException("broker unavailable")).when(messaging)
                .convertAndSend(anyString(), any(Object.class));
        assertDoesNotThrow(() -> new TransactionTemplate(transactions).executeWithoutResult(tx ->
                events.publishEvent(new ChannelMessageDelivery.Created(saved()))));
    }

    @Test
    void memberCanSubscribeButRemovedMemberCannotReceiveFurtherMessages() {
        connect("member-session", Instant.now().plusSeconds(60));
        when(service.isChannelMember(5L, "2")).thenReturn(true, false);
        Message<?> subscription = stomp(StompCommand.SUBSCRIBE, "member-session", "/topic/channels/5");
        assertSame(subscription, inbound.preSend(subscription, channel));
        assertNull(outbound.preSend(delivery("member-session", "/topic/channels/5"), channel));
    }

    @Test
    void checksEveryRecipientsMembershipIndependently() {
        connect("allowed", Instant.now().plusSeconds(60));
        connect("removed", Instant.now().plusSeconds(60));
        when(service.isChannelMember(5L, "2")).thenReturn(false, true);
        assertNull(outbound.preSend(delivery("removed", "/topic/channels/5"), channel));
        assertNotNull(outbound.preSend(delivery("allowed", "/topic/channels/5"), channel));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/channels/*", "/topic/**", "/topic/channels/?",
            "/topic/channels/{id}", "/topic/channels/9999999999999999999999999",
            "/topic/channels/0", "/queue/**", "/queue/channel-message-results-user-other",
            "/user/other/queue/channel-message-results"})
    void rejectsWildcardMalformedAndRawResultQueueSubscriptions(String destination) {
        connect("member-session", Instant.now().plusSeconds(60));
        assertThrows(MessageDeliveryException.class,
                () -> inbound.preSend(stomp(StompCommand.SUBSCRIBE, "member-session", destination), channel));
    }

    @Test
    void outsiderAndAnonymousCannotSubscribe() {
        connect("outsider", Instant.now().plusSeconds(60));
        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(
                stomp(StompCommand.SUBSCRIBE, "outsider", "/topic/channels/5"), channel));
        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(
                stomp(StompCommand.SUBSCRIBE, "anonymous", "/topic/channels/5"), channel));
        assertThrows(MessageDeliveryException.class, () -> inbound.preSend(
                stomp(StompCommand.SUBSCRIBE, "anonymous", "/user/queue/channel-message-results"), channel));
    }

    @Test
    void invalidJwtDoesNotGrantChannelAccess() {
        when(decoder.decode("invalid")).thenThrow(new JwtException("invalid"));
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionId("invalid-session");
        headers.setNativeHeader("Authorization", "Bearer invalid");
        headers.setLeaveMutable(true);
        inbound.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), channel);
        assertNull(outbound.preSend(delivery("invalid-session", "/topic/channels/5"), channel));
        verifyNoInteractions(service);
    }

    @Test
    void expiredAndDisconnectedSessionsCannotReceive() {
        connect("expired", Instant.now().minusSeconds(1));
        assertNull(outbound.preSend(delivery("expired", "/topic/channels/5"), channel));
        connect("closed", Instant.now().plusSeconds(60));
        inbound.preSend(stomp(StompCommand.DISCONNECT, "closed", null), channel);
        assertNull(outbound.preSend(delivery("closed", "/topic/channels/5"), channel));
        verifyNoInteractions(service);
    }

    @Test
    void failedMembershipLookupDropsDelivery() {
        connect("member-session", Instant.now().plusSeconds(60));
        when(service.isChannelMember(5L, "2")).thenThrow(new IllegalStateException("database unavailable"));
        assertNull(outbound.preSend(delivery("member-session", "/topic/channels/5"), channel));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/channels/5", "/queue/channel-message-results-user-other",
            "/user/other/queue/channel-message-results"})
    void clientCannotForgeMessagesBySendingDirectlyToBroker(String destination) {
        assertThrows(MessageDeliveryException.class,
                () -> inbound.preSend(stomp(StompCommand.SEND, "attacker", destination), channel));
    }

    @Test
    void unrelatedDmAndFeedSubscriptionsKeepExistingBehavior() {
        for (String destination : List.of("/topic/conversations/9", "/user/queue/new-post",
                "/user/queue/friend-requests/received")) {
            Message<?> frame = stomp(StompCommand.SUBSCRIBE, "session", destination);
            assertSame(frame, inbound.preSend(frame, channel));
            Message<?> delivered = delivery("session", destination);
            assertSame(delivered, outbound.preSend(delivered, channel));
        }
    }

    @Test
    void successQueueResolvesOnlyToOriginatingTabEvenWithoutUserRegistryEntry() {
        connect("tab-a", Instant.now().plusSeconds(60));
        assertNotNull(inbound.preSend(stomp(StompCommand.SUBSCRIBE, "tab-a",
                "/user/queue/channel-message-results"), channel));
        DefaultUserDestinationResolver resolver = new DefaultUserDestinationResolver(mock(SimpUserRegistry.class));
        Message<?> result = delivery("tab-a", "/user/tab-a/queue/channel-message-results");
        assertEquals(Set.of("/queue/channel-message-results-usertab-a"),
                resolver.resolveDestination(result).getTargetDestinations());
    }

    private MessageResponse saved() {
        return new MessageResponse(101L, 5L, 2L, "Hello", Instant.now());
    }

    private void connect(String sessionId, Instant expiresAt) {
        Jwt jwt = Jwt.withTokenValue(sessionId).header("alg", "HS256").subject("member@example.test")
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(expiresAt)
                .claim("user", Map.of("id", 2)).build();
        when(decoder.decode(sessionId)).thenReturn(jwt);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionId(sessionId);
        headers.setNativeHeader("Authorization", "Bearer " + sessionId);
        headers.setLeaveMutable(true);
        inbound.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), channel);
    }

    private Message<?> stomp(StompCommand command, String sessionId, String destination) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId(sessionId);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private Message<?> delivery(String sessionId, String destination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private static class Registration extends ChannelRegistration {
        List<ChannelInterceptor> interceptors() { return getInterceptors(); }
    }
}
