package com.qros.shared.event;

import org.springframework.data.redis.core.StringRedisTemplate;

/** Fan-out bằng Redis pub/sub — thay cho message broker, xem SDD mục 3.1 và {@code ADR-05}. */
public class RedisOutboxPublisher implements OutboxPublisher {

    private final StringRedisTemplate redisTemplate;

    public RedisOutboxPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }
}
