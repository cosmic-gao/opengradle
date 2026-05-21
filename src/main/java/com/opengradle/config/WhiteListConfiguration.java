package com.opengradle.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Whitelisted URI patterns (regex) that bypass the auth check.
 * Bound from configuration prefix {@code gateway.whitelist}.
 *
 * <pre>
 * gateway:
 *   whitelist:
 *     - sys/login
 *     - sys/share/.*
 * </pre>
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "gateway")
public class WhiteListConfiguration {

    /** Regex patterns; an inbound URI matching any one of them bypasses auth. */
    private List<String> whitelist = new ArrayList<>();
}
