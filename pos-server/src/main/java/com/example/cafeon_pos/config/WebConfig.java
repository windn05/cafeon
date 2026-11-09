package com.example.cafeon_pos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig {

    // 본사 ERP 호출용. 본사가 꺼져 있으면 오래 기다리지 않고 대기열에 남긴다.
    @Bean
    public RestTemplate hqRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }

    // CORS는 인증 필터보다 먼저 처리해야 401 응답도 브라우저가 읽을 수 있다.
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(@Value("${pos.front-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(0);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<PosAuthFilter> posAuthFilter(PosTokens tokens) {
        FilterRegistrationBean<PosAuthFilter> bean = new FilterRegistrationBean<>(new PosAuthFilter(tokens));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }
}
