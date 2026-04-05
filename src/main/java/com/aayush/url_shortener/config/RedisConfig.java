package com.aayush.url_shortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Explicit Redis configuration.
 *
 * Spring Boot auto-configures Redis, but defining this bean explicitly:
 * - Makes the serialization strategy clear and intentional
 * - Ensures keys and values are stored as plain strings (human-readable in Redis CLI)
 * - Gives us a single place to change Redis behaviour if needed later
 *
 * StringRedisTemplate is a specialised RedisTemplate where both
 * key and value serializers are set to StringRedisSerializer by default.
 * We make that explicit here rather than relying on convention.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}