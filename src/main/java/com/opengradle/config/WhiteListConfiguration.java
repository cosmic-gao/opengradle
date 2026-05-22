package com.opengradle.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 鉴权白名单的正则集合 —— 命中即跳过鉴权。绑定到配置前缀 {@code gateway.whitelist}。
 *
 * <pre>
 * gateway:
 *   whitelist:
 *     - sys/login
 *     - sys/share/.*
 * </pre>
 *
 * <p>带 {@code @RefreshScope},Nacos 推送后无需重启即可生效。
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "gateway")
public class WhiteListConfiguration {

    /** 正则列表;只要请求路径(去掉前导 "/")匹配其中任一条,就放行不鉴权。 */
    private List<String> whitelist = new ArrayList<>();
}
