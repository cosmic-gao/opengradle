package com.opengradle.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * Hot-reloadable authentication settings, bound from {@code auth.*}.
 *
 * <p>{@code @RefreshScope} means a Nacos {@code refresh} event re-binds the
 * values without restarting the app. Consumers should inject this bean (not
 * raw {@code @Value} fields) to benefit from the hot reload.
 *
 * <pre>
 * auth:
 *   token-ttl-seconds: 7200
 * </pre>
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "auth")
public class AuthConfiguration {

    /**
     * Token TTL in seconds; refreshed on each successful login.
     * Default 7200 (2 hours).
     */
    private long tokenTtlSeconds = 7200;
}
