package com.denhub.config;

import com.denhub.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.MessageDeliveryException;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher eventPublisher;
    private final JwtDecoder jwtDecoder;
    private final com.denhub.service.MessageService messageService;
    private final Map<String, Authentication> channelSessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> channelSessionExpiry = new ConcurrentHashMap<>();

    public WebSocketConfig(SecurityUtils securityUtils, ApplicationEventPublisher eventPublisher, JwtDecoder jwtDecoder,
                           @org.springframework.context.annotation.Lazy com.denhub.service.MessageService messageService) {
        this.messageService = messageService;
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue") // topic: 1 gửi cho tất cả, queue : 1 gửi 1 (đổi key tùy thích)
                // in 10s sẽ gửi 1 lần nếu detect user disconnected or turn off wifi => disconnect
                .setHeartbeatValue(new long[] { 10000, 10000 }) // 10s heartbeat
                .setTaskScheduler(taskScheduler());
        // FE send
        // publish({
        //   destination: "/app/room.play"
        //})
        // BE accept
        // @MessageMapping("/room.play")
        config.setApplicationDestinationPrefixes("/app");
    }

    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            Jwt jwt = jwtDecoder.decode(token);
                            Object userClaim = jwt.getClaim("user");
                            String principalName = jwt.getSubject();
                            if (userClaim instanceof Map<?, ?> userMap) {
                                Object idObj = userMap.get("id");
                                if (idObj != null) {
                                    principalName = idObj.toString();
                                }
                            }
                            Authentication auth = new UsernamePasswordAuthenticationToken(principalName, null, List.of());
                            accessor.setUser(auth);
                            if (accessor.getSessionId() != null) {
                                channelSessions.put(accessor.getSessionId(), auth);
                                if (jwt.getExpiresAt() != null) {
                                    channelSessionExpiry.put(accessor.getSessionId(), jwt.getExpiresAt());
                                }
                            }
                        } catch (Exception ignored) {
                            // Token decode failure or unauthenticated
                        }
                    }
                }
                if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    forgetSession(accessor.getSessionId());
                }
                if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    // Clients must not inject forged channel events or send results into the broker.
                    if (destination != null && (destination.startsWith("/topic/")
                            || destination.startsWith("/queue/") || destination.startsWith("/user/"))) {
                        throw new MessageDeliveryException("Không được gửi trực tiếp vào broker");
                    }
                }
                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null) {
                        // SimpleBroker supports wildcard subscriptions. Reject patterns that bypass exact channel checks.
                        if ((destination.startsWith("/topic") || destination.startsWith("/queue")
                                || destination.startsWith("/user")) && (destination.contains("*")
                                || destination.contains("?") || destination.contains("{"))) {
                            throw new MessageDeliveryException("Topic phải là địa chỉ cụ thể");
                        }
                        if (destination.startsWith("/topic/channels")) {
                            Long channelId = channelId(destination);
                            if (channelId == null || !canReceive(channelId, accessor.getSessionId())) {
                                throw new MessageDeliveryException("Không có quyền subscribe Channel này");
                            }
                        }
                        if ((destination.contains("channel-features") && destination.startsWith("/queue/"))
                                || (destination.startsWith("/user/") && destination.contains("channel-features")
                                && (!destination.equals("/user/queue/channel-features") || !activeSession(accessor.getSessionId())))) {
                            throw new MessageDeliveryException("Không có quyền subscribe tùy chọn cá nhân");
                        }
                        if (destination.startsWith("/queue/channel-message-results")
                                || (destination.startsWith("/user/")
                                && destination.contains("channel-message-results")
                                && (!destination.equals("/user/queue/channel-message-results")
                                || !validSession(accessor.getSessionId())))) {
                            throw new MessageDeliveryException("Không có quyền subscribe kết quả gửi");
                        }
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                org.springframework.messaging.simp.SimpMessageHeaderAccessor headers =
                        org.springframework.messaging.simp.SimpMessageHeaderAccessor.wrap(message);
                String destination = headers.getDestination();
                if (destination != null && destination.contains("/queue/channel-features") && !activeSession(headers.getSessionId())) return null;
                if (destination != null && destination.startsWith("/topic/channels")) {
                    Long id = channelId(destination);
                    if (id == null || !canReceive(id, headers.getSessionId())) return null;
                }
                return message;
            }
        });
    }

    private Long channelId(String destination) {
        if (!destination.matches("^/topic/channels/[1-9][0-9]*$")) return null;
        try {
            return Long.valueOf(destination.substring("/topic/channels/".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean activeSession(String id) {
        return validSession(id) && messageService.isActivePrincipal(channelSessions.get(id).getName());
    }

    private boolean validSession(String id) {
        if (id == null || !channelSessions.containsKey(id)) return false;
        Instant expiry = channelSessionExpiry.get(id);
        return expiry == null || expiry.isAfter(Instant.now());
    }

    private boolean canReceive(Long channelId, String sessionId) {
        if (!validSession(sessionId)) return false;
        try {
            return messageService.isChannelMember(channelId, channelSessions.get(sessionId).getName());
        } catch (RuntimeException ex) {
            // Fail closed if membership cannot be checked, without failing delivery to other sessions.
            return false;
        }
    }

    private void forgetSession(String id) {
        if (id != null) {
            channelSessions.remove(id);
            channelSessionExpiry.remove(id);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        forgetSession(event.getSessionId());
    }
}
