package com.qros.shared.realtime;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSocketMessageBroker
public class RealtimeConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/v1/staff/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        // Spring Boot 4 dùng Jackson 3 (tools.jackson); converter Jackson 2 mặc định không đọc được payload.
        messageConverters.add(new JacksonJsonMessageConverter());
        return false;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && accessor.getDestination() != null
                        && accessor.getDestination().startsWith("/topic/kds/")) {
                    RealtimeAccess.requireKdsStore(accessor.getUser(), accessor.getDestination());
                }
                return message;
            }
        });
    }

    @Bean
    public RedisMessageListenerContainer realtimeRedisListener(RedisConnectionFactory connectionFactory,
            SimpMessagingTemplate messagingTemplate) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String storeId = channel.substring("qros:store:".length());
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            messagingTemplate.convertAndSend("/topic/kds/" + storeId, body);
        }, new PatternTopic("qros:store:*"));
        return container;
    }
}
