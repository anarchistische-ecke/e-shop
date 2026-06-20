package com.example.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

@Configuration
public class PaymentConfig {
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @org.springframework.beans.factory.annotation.Value("${yookassa.connect-timeout:3s}") Duration connectTimeout,
            @org.springframework.beans.factory.annotation.Value("${yookassa.read-timeout:5s}") Duration readTimeout
    ) {
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
