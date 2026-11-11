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

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher eventPublisher;
    private final JwtDecoder jwtDecoder;

    public WebSocketConfig(SecurityUtils securityUtils, ApplicationEventPublisher eventPublisher, JwtDecoder jwtDecoder) {
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
                        } catch (Exception ignored) {
                            // Token decode failure or unauthenticated
                        }
                    }
                }
                return message;
            }
        });
    }
}
