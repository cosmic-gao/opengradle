package com.opengradle.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opengradle.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Translates uncaught exceptions and downstream 5xx into a unified JSON envelope.
 *
 * <p>Ordered with {@code @Order(-1)} so it runs before the default
 * {@code DefaultErrorWebExceptionHandler}.
 */
@Slf4j
@Component
@Order(-1)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        log.error("[GlobalExceptionHandler] path={}, status={}, msg={}",
                exchange.getRequest().getPath(), status, ex.getMessage(), ex);

        Result<Void> body = Result.fail(status.value(), safeMessage(ex));
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializeErr) {
            bytes = ("{\"code\":" + status.value() + ",\"msg\":\"internal error\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            HttpStatus s = ((ResponseStatusException) ex).getStatus();
            return s != null ? s : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ex instanceof NotFoundException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String safeMessage(Throwable ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
