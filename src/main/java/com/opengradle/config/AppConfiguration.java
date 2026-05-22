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
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 应用级配置:
 * - LocalDate / LocalDateTime 的 Jackson 序列化格式
 * - 带负载均衡能力的 WebClient(支持 lb://service-name 这类 URL)
 * - 查询参数 / 路径参数的 String ↔ 日期 转换器
 */
@Configuration
public class AppConfiguration implements WebFluxConfigurer {

    @Value("${spring.jackson.date-format:yyyy-MM-dd'T'HH:mm:ss'Z'}")
    private String localDateTimePattern;

    @Value("${spring.codec.max-in-memory-size:10MB}")
    private DataSize maxInMemorySize;

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
     * 带负载均衡能力的 WebClient,可以通过 {@code lb://service-name} 调用 Nacos 中的服务。
     */
    @Bean
    public WebClient webClient(ReactorLoadBalancerExchangeFilterFunction lbFunction) {
        return WebClient.builder()
                .filter(lbFunction)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize((int) maxInMemorySize.toBytes()))
                .build();
    }
}
