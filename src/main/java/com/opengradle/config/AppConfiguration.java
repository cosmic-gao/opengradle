package com.opengradle.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Application-wide configuration:
 * - Jackson serializers for LocalDate / LocalDateTime
 * - WebClient with load-balancer support (so it can call lb://service-name)
 * - String &lt;-&gt; date converters for query/path params
 */
@Configuration
public class AppConfiguration implements WebFluxConfigurer {

    @Value("${spring.jackson.date-format:yyyy-MM-dd'T'HH:mm:ss'Z'}")
    private String localDateTimePattern;

    @Value("${spring.codec.max-in-memory-size:10485760}")
    private int maxInMemorySize;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dataTimeJacksonMapperCustomizer() {
        return builder -> builder.serializersByType(Map.of(
                LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(localDateTimePattern)),
                LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE)
        ));
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToLocalDateTimeConverter());
        registry.addConverter(new StringToLocalDateConverter());
    }

    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(String source) {
            return LocalDateTime.parse(source, DateTimeFormatter.ISO_DATE_TIME);
        }
    }

    static class StringToLocalDateConverter implements Converter<String, LocalDate> {
        @Override
        public LocalDate convert(String source) {
            return LocalDate.parse(source, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    /**
     * WebClient with load-balancer support — can call {@code lb://service-name}.
     */
    @Bean
    public WebClient webClient(ReactorLoadBalancerExchangeFilterFunction lbFunction) {
        return WebClient.builder()
                .filter(lbFunction)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize))
                .build();
    }
}
