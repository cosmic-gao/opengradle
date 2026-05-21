package com.opengradle.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.opengradle.auth.AuthService;
import com.opengradle.auth.UserContext;
import com.opengradle.config.WhiteListConfiguration;
import com.opengradle.constant.Constant;
import com.opengradle.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Token-based auth global filter.
 *
 * <p>Pipeline per request:
 * <ol>
 *   <li>If the URI matches a whitelist regex (configured via {@code gateway.whitelist}),
 *       the request bypasses authentication entirely.</li>
 *   <li>Otherwise the {@code token} header is resolved through {@link AuthService}
 *       to a {@link UserContext}. Absence or expiry results in HTTP 401 with a JSON body.</li>
 *   <li>On success, identity headers ({@code x-user-id}, {@code x-username},
 *       {@code x-tenant-code}, {@code x-super-admin}) are injected so downstream
 *       services trust the gateway and do not re-validate tokens.</li>
 *   <li>The resolved client IP is forwarded via {@code x-forwarded-for}.</li>
 * </ol>
 */
@Slf4j
@Order(10)
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter {

    private static final String ERR_TEMPLATE = "{\"code\":{},\"msg\":\"{}\",\"data\":null}";

    private final WhiteListConfiguration whiteListConfiguration;
    private final AuthService authService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        String path = request.getPath().pathWithinApplication().value();
        String clientIp = resolveClientIp(request);

        // 1) whitelist bypass — but still inject the client IP for downstream
        if (matchesWhitelist(whiteListConfiguration.getWhitelist(), path)) {
            log.debug("auth: whitelisted, path={}", path);
            return chain.filter(withClientIp(exchange, clientIp));
        }

        // 2) token must exist
        String token = headers.getFirst(Constant.TOKEN_HEADER);
        if (CharSequenceUtil.isBlank(token)) {
            return unauthorized(exchange, "token is missing.");
        }

        // 3) token must resolve to a UserContext
        Optional<UserContext> ctxOpt;
        try {
            ctxOpt = authService.getUserContext(token);
        } catch (Exception e) {
            log.error("auth: getUserContext failed: {}", e.getMessage(), e);
            return unauthorized(exchange, "auth backend unavailable.");
        }
        if (!ctxOpt.isPresent()) {
            return unauthorized(exchange, "token is invalid or expired.");
        }
        UserContext ctx = ctxOpt.get();

        // 4) inject identity headers + forwarded IP for downstream
        ServerWebExchange mutated = withIdentityHeaders(exchange, ctx, clientIp);
        log.debug("auth: ok, userId={}, tenant={}, path={}", ctx.getUserId(), ctx.getTenantCode(), path);
        return chain.filter(mutated);
    }

    // ---------------------------------------------------------------- helpers

    private ServerWebExchange withClientIp(ServerWebExchange exchange, String clientIp) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(Constant.X_FORWARDED_FOR, clientIp)
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, UserContext ctx, String clientIp) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .header(Constant.X_FORWARDED_FOR, clientIp)
                .header(Constant.HEADER_SUPER_ADMIN, String.valueOf(ctx.isSuperAdmin()));

        if (ctx.getUserId() != null) {
            builder.header(Constant.HEADER_USER_ID, String.valueOf(ctx.getUserId()));
        }
        if (CharSequenceUtil.isNotBlank(ctx.getUsername())) {
            builder.header(Constant.HEADER_USERNAME, ctx.getUsername());
        }
        if (ctx.getTenantCode() != null) {
            builder.header(Constant.HEADER_TENANT_CODE_INJECTED, String.valueOf(ctx.getTenantCode()));
        }
        return exchange.mutate().request(builder.build()).build();
    }

    private boolean matchesWhitelist(List<String> patterns, String uri) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        String normalized = uri.length() == 1 ? uri : uri.substring(1);
        for (String pattern : patterns) {
            try {
                if (normalized.matches(pattern)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("auth: invalid whitelist regex '{}': {}", pattern, e.getMessage());
            }
        }
        return false;
    }

    private String resolveClientIp(ServerHttpRequest request) {
        // Prefer existing X-Forwarded-For (set by upstream proxies/LB).
        String xff = request.getHeaders().getFirst(Constant.X_FORWARDED_FOR);
        if (CharSequenceUtil.isNotBlank(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = StrUtil.format(ERR_TEMPLATE, ErrorCode.UNAUTHORIZED, message);
        return response.writeWith(toDataBuffer(exchange, json));
    }

    private Flux<DataBuffer> toDataBuffer(ServerWebExchange exchange, String json) {
        return Flux.just(exchange.getResponse().bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8)));
    }
}
