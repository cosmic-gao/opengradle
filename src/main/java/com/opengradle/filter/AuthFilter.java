package com.opengradle.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.opengradle.auth.TokenStore;
import com.opengradle.auth.UserContext;
import com.opengradle.config.WhiteListConfiguration;
import com.opengradle.constant.Constant;
import com.opengradle.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
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
 * 基于 token 的全局鉴权过滤器。
 *
 * <p>每个请求的处理顺序:
 * <ol>
 *   <li>命中 {@code gateway.whitelist} 中的正则则跳过鉴权,直接放行。</li>
 *   <li>否则取 {@code token} 请求头,通过 {@link TokenStore} 解析为
 *       {@link UserContext};缺失或失效则返回 401(JSON 响应体)。</li>
 *   <li>成功后注入身份头 ({@code x-user-id}、{@code x-username}、
 *       {@code x-tenant-code}、{@code x-super-admin}),下游服务直接信任,
 *       不再重复校验 token。</li>
 *   <li>解析出的客户端真实 IP 通过 {@code x-forwarded-for} 透传给下游。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private static final String ERR_TEMPLATE = "{\"code\":{},\"msg\":\"{}\",\"data\":null}";

    private final WhiteListConfiguration whiteListConfiguration;
    private final TokenStore tokenStore;

    /** 必须先于 {@code RouteToRequestUrlFilter}(10000)和负载均衡过滤器(10150)执行。 */
    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        String path = request.getPath().pathWithinApplication().value();
        String clientIp = resolveClientIp(request);

        // 1) 白名单放行 —— 但仍要把客户端 IP 透传给下游
        if (matchesWhitelist(whiteListConfiguration.getWhitelist(), path)) {
            log.debug("auth: whitelisted, path={}", path);
            return chain.filter(withClientIp(exchange, clientIp));
        }

        // 2) token 必须存在
        String token = headers.getFirst(Constant.TOKEN_HEADER);
        if (CharSequenceUtil.isBlank(token)) {
            return unauthorized(exchange, "token is missing.");
        }

        // 3) token 必须能解析出 UserContext
        Optional<UserContext> ctxOpt = tokenStore.get(token);
        if (!ctxOpt.isPresent()) {
            return unauthorized(exchange, "token is invalid or expired.");
        }
        UserContext ctx = ctxOpt.get();

        // 4) 注入身份头 + 透传客户端 IP
        ServerWebExchange mutated = withIdentityHeaders(exchange, ctx, clientIp);
        log.debug("auth: ok, userId={}, tenant={}, path={}", ctx.getUserId(), ctx.getTenantCode(), path);
        return chain.filter(mutated);
    }

    // ---------------------------------------------------------------- 辅助方法

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
        // 优先使用已有的 X-Forwarded-For(上游代理 / 负载均衡器写入)。
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
