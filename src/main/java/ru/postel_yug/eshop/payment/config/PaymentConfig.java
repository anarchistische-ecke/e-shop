package ru.postel_yug.eshop.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class PaymentConfig {
    @Value("${app.lifepay.apiUrl}")
    private String lifepayApiUrl;
    @Value("${app.lifepay.apiKey}")
    private String lifepayApiKey;
    @Value("${app.lifepay.apiSecret}")
    private String lifepayApiSecret;

    @Bean
    public WebClient lifepayWebClient() {
        return WebClient.builder()
                .baseUrl(lifepayApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodeBasicAuth(lifepayApiKey, lifepayApiSecret))
                .build();
    }
    private String encodeBasicAuth(String key, String secret) {
        String creds = key + ":" + secret;
        return Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }
}
