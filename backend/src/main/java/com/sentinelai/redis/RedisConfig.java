package com.sentinelai.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/** Builds the Redis connection + template only when {@code sentinel.redis.enabled=true}. */
@Configuration
@ConditionalOnProperty(prefix = "sentinel.redis", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProperties props;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        var standalone = new RedisStandaloneConfiguration(props.getHost(), props.getPort());
        Duration timeout = Duration.ofMillis(props.getTimeoutMs());
        var client = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                        .build())
                .build();
        var factory = new LettuceConnectionFactory(standalone, client);
        factory.setValidateConnection(false);
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
