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

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher eventPublisher;
    public WebSocketConfig(SecurityUtils securityUtils, ApplicationEventPublisher eventPublisher) {
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
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
                .setAllowedOriginPatterns("*")
                .withSockJS();
//        registry.addEndpoint("/ws")
//                .setAllowedOriginPatterns("*");
    }
}
