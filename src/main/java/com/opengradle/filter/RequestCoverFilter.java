package com.opengradle.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.opengradle.config.GatewayContext;
import io.netty.buffer.ByteBufAllocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Caches the request body into the {@link GatewayContext} attribute so that
 * downstream filters can inspect it without consuming the reactive stream.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} to ensure the cache is
 * available to every other filter.
 */
@Slf4j
@Component
public class RequestCoverFilter implements GlobalFilter, Ordered {

    private final List<HttpMessageReader<?>> messageReaders;

    @Autowired
    public RequestCoverFilter(ServerCodecConfigurer codecConfigurer) {
        this.messageReaders = codecConfigurer.getReaders();
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Initialize and store the per-request context.
        GatewayContext gatewayContext = new GatewayContext();
        String path = request.getPath().pathWithinApplication().value();
        gatewayContext.setPath(path);
        gatewayContext.getFormData().addAll(request.getQueryParams());
        if (request.getRemoteAddress() != null) {
            gatewayContext.setIpAddress(String.valueOf(request.getRemoteAddress()));
        }
        HttpHeaders headers = request.getHeaders();
        gatewayContext.setHeaders(headers);
        exchange.getAttributes().put(GatewayContext.CACHE_GATEWAY_CONTEXT, gatewayContext);

        MediaType contentType = headers.getContentType();
        long contentLength = headers.getContentLength();
        if (contentLength > 0 && contentType != null) {
            if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                return readJsonBody(exchange, chain, gatewayContext);
            }
            if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
                return readFormData(exchange, chain, gatewayContext);
            }
        }

        log.debug("[GatewayContext] contentType={}, path={}", contentType, path);
        return chain.filter(exchange);
    }

    /**
     * Joins the entire request body, parses it as JSON, caches it,
     * and replaces the request with a decorator that re-emits the cached bytes.
     */
    private Mono<Void> readJsonBody(ServerWebExchange exchange, GatewayFilterChain chain,
                                    GatewayContext gatewayContext) {
        return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);

            Flux<DataBuffer> cachedFlux = Flux.defer(() -> {
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                DataBufferUtils.retain(buffer);
                return Mono.just(buffer);
            });

            ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return cachedFlux;
                }
            };
            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

            return ServerRequest.create(mutatedExchange, messageReaders)
                    .bodyToMono(JsonNode.class)
                    .doOnNext(json -> {
                        gatewayContext.setCacheBody(json);
                        log.debug("[GatewayContext] cached JSON body, size={}", bytes.length);
                    })
                    .then(chain.filter(mutatedExchange));
        });
    }

    /**
     * Reads form data into the context and re-emits a URL-encoded body so
     * downstream HTTP forwarding still sees a valid form payload.
     */
    private Mono<Void> readFormData(ServerWebExchange exchange, GatewayFilterChain chain,
                                    GatewayContext gatewayContext) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();

        return exchange.getFormData().doOnNext(multiValueMap -> {
            gatewayContext.setFormData(multiValueMap);
            log.debug("[GatewayContext] cached FormData, keys={}", multiValueMap.keySet());
        }).then(Mono.defer(() -> {
            Charset charset = headers.getContentType() != null
                    ? headers.getContentType().getCharset() : null;
            charset = charset == null ? StandardCharsets.UTF_8 : charset;
            String charsetName = charset.name();
            MultiValueMap<String, String> formData = gatewayContext.getFormData();
            if (formData == null || formData.isEmpty()) {
                return chain.filter(exchange);
            }
            StringBuilder builder = new StringBuilder(1024);
            try {
                for (Map.Entry<String, List<String>> entry : formData.entrySet()) {
                    for (String value : entry.getValue()) {
                        builder.append(entry.getKey()).append('=')
                                .append(URLEncoder.encode(value, charsetName)).append('&');
                    }
                }
            } catch (UnsupportedEncodingException ignored) {
                // unreachable for UTF-8
            }
            if (builder.length() > 0) {
                builder.setLength(builder.length() - 1);
            }
            byte[] bodyBytes = builder.toString().getBytes(charset);
            int contentLength = bodyBytes.length;

            ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(request) {
                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders newHeaders = new HttpHeaders();
                    newHeaders.putAll(super.getHeaders());
                    if (contentLength > 0) {
                        newHeaders.setContentLength(contentLength);
                    } else {
                        newHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                    }
                    return newHeaders;
                }

                @Override
                public Flux<DataBuffer> getBody() {
                    return DataBufferUtils.read(new ByteArrayResource(bodyBytes),
                            new NettyDataBufferFactory(ByteBufAllocator.DEFAULT), contentLength);
                }
            };
            ServerWebExchange mutated = exchange.mutate().request(decorator).build();
            return chain.filter(mutated);
        }));
    }
}
