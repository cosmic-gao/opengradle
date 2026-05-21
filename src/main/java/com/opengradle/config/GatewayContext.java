package com.opengradle.config;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Per-request context bag stored on the {@code ServerWebExchange} attributes.
 *
 * <p>Filters that need to inspect the request body more than once read the body
 * stream once into this context, then later filters can read the cached value
 * without consuming the underlying {@code DataBuffer} stream.
 */
@Data
public class GatewayContext {

    public static final String CACHE_GATEWAY_CONTEXT = "cacheGatewayContext";

    /** Cached request headers. */
    private HttpHeaders headers;

    /** Cached JSON body (parsed). */
    private JsonNode cacheBody;

    /** Cached form-urlencoded data. */
    private MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

    /** Original client IP. */
    private String ipAddress;

    /** Request path. */
    private String path;
}
