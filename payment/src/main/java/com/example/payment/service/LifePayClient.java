package com.example.payment.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class LifePayClient {
    private final RestTemplate restTemplate;

    @Value("${lifepay.base-url}")
    private String baseUrl;
    @Value("${lifepay.service-id}")
    private int serviceId;
    @Value("${lifepay.api-key}")
    private String apiKey;
    @Value("${lifepay.notification-url}")
    private String notificationUrl;

    public LifePayClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String authenticate() {
        String url = baseUrl + "/auth";
        Map<String, Object> authRequest = new HashMap<>();
        authRequest.put("service_id", serviceId);
        authRequest.put("api_key", apiKey);
        LifePayAuthResponse response = restTemplate.postForObject(url, authRequest, LifePayAuthResponse.class);
        if (response == null || response.jwt == null) {
            throw new IllegalStateException("Failed to authenticate with LifePay API");
        }
        return response.jwt;
    }

    public LifePayInvoiceResponse createInvoice(String jwtToken, String orderId, String amount,
                                                String currency, String name, String email, String phone) {
        String url = baseUrl + "/invoices";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        Map<String, Object> body = new HashMap<>();
        body.put("order_id", orderId);
        body.put("amount", amount);
        body.put("currency_code", currency);
        body.put("service_id", serviceId);
        body.put("name", name);
        if (email != null) body.put("email", email);
        if (phone != null) body.put("phone", phone);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(url, entity, LifePayInvoiceResponse.class);
    }

    public LifePayTokenResponse createPaymentToken(String jwtToken, UUID invoiceId, String cardToken) {
        String url = baseUrl + "/invoices/" + invoiceId + "/payment_tokens";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        Map<String, Object> body = new HashMap<>();
        body.put("payment_type", "card");
        body.put("raw", cardToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(url, entity, LifePayTokenResponse.class);
    }

    public LifePayChargeResponse createCharge(String jwtToken, UUID invoiceId, UUID paymentTokenId, BrowserInfo browserInfo) {
        String url = baseUrl + "/invoices/" + invoiceId + "/charges";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        Map<String, Object> device = new HashMap<>();
        device.put("BrowserIP", browserInfo.getIp());
        device.put("BrowserAcceptHeader", browserInfo.getAcceptHeader());
        device.put("BrowserJavaScriptEnabled", browserInfo.isJavascriptEnabled());
        device.put("BrowserLanguage", browserInfo.getLanguage());
        device.put("BrowserScreenHeight", browserInfo.getScreenHeight());
        device.put("BrowserScreenWidth", browserInfo.getScreenWidth());
        device.put("BrowserTimeZone", browserInfo.getTimezoneOffset());
        device.put("BrowserUserAgent", browserInfo.getUserAgent());
        device.put("BrowserJavaEnabled", browserInfo.isJavaEnabled());
        device.put("BrowserScreenColorDepth", browserInfo.getColorDepth());
        Map<String, Object> body = new HashMap<>();
        body.put("payment_token_id", paymentTokenId);
        body.put("device", device);
        body.put("notification_url", notificationUrl);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(url, entity, LifePayChargeResponse.class);
    }

    public LifePayChargeStatusResponse getChargeStatus(String jwtToken, UUID invoiceId, UUID chargeId) {
        String url = baseUrl + "/invoices/" + invoiceId + "/charges/" + chargeId;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity,
                LifePayChargeStatusResponse.class).getBody();
    }

    public static class LifePayAuthResponse {
        public String jwt;
        @JsonAlias("lp_public")
        public String lpPublic;
    }
    public static class LifePayInvoiceResponse {
        public UUID id;
        public String status;
        @JsonAlias("form_link")
        public String formLink;

    }
    public static class LifePayTokenResponse {
        public UUID id;
        public String status;
        public String message;
    }
    public static class LifePayChargeResponse {
        public UUID id;
        @JsonAlias("payment_token_id")
        public UUID paymentTokenId;
        public String status;
        public String message;
    }
    public static class LifePayChargeStatusResponse {
        public UUID id;
        public String status;
        public String message;
        @JsonAlias("acs_url")
        public String acsUrl;
        @JsonAlias("acs_data")
        public Map<String, String> acsData;
        @JsonAlias("decline_code")
        public String declineCode;
    }
}
