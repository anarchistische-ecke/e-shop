package ru.postel_yug.eshop.shipping.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class CdekAuthService {
    @Value("${app.cdek.account}")
    private String account;
    @Value("${app.cdek.securePassword}")
    private String secure;
    @Autowired
    private WebClient cdekWebClient;

    private volatile String token;
    private volatile LocalDateTime tokenExpiry;

    public String getToken() {
        if(token == null || LocalDateTime.now().isAfter(tokenExpiry)) {
            refreshToken();
        }
        return token;
    }

    private synchronized void refreshToken() {
        if(token != null && LocalDateTime.now().isBefore(tokenExpiry)) {
            return; // уже обновлен другим потоком
        }
        // Запрос на получение токена
        Map<String,String> response = cdekWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/v2/oauth/token")
                        .queryParam("grant_type", "client_credentials")
                        .queryParam("client_id", account)
                        .queryParam("client_secret", secure).build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String,String>>() {})
                .block();
        if(response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Не удалось получить токен CDEK");
        }
        token = response.get("access_token");
        // API возвращает expires_in (секунды), можно использовать для вычисления tokenExpiry
        long expiresIn = Long.parseLong(response.getOrDefault("expires_in", "3600"));
        tokenExpiry = LocalDateTime.now().plusSeconds(expiresIn - 60); // обновим за минуту до истечения
    }
}

