package com.example.payment.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "yookassa", name = "enabled", havingValue = "true", matchIfMissing = false)
public class YooKassaClient {
    private final RestTemplate restTemplate;

    @Value("${yookassa.base-url:https://api.yookassa.ru/v3}")
    private String baseUrl;
    @Value("${yookassa.shop-id:}")
    private String shopId;
    @Value("${yookassa.secret-key:}")
    private String secretKey;
    @Value("${yookassa.vat-code:1}")
    private int vatCode;
    @Value("${yookassa.tax-system-code:1}")
    private int taxSystemCode;

    public YooKassaClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public int getVatCode() {
        return vatCode;
    }

    public int getTaxSystemCode() {
        return taxSystemCode;
    }

    public CreatePaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        String url = baseUrl + "/payments";
        HttpHeaders headers = buildHeaders(idempotencyKey);
        HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.postForObject(url, entity, CreatePaymentResponse.class);
    }

    public CreatePaymentResponse getPayment(String paymentId) {
        String url = baseUrl + "/payments/" + paymentId;
        HttpHeaders headers = buildHeaders(null);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, CreatePaymentResponse.class)
                .getBody();
    }

    private HttpHeaders buildHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(shopId, secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotence-Key", idempotencyKey);
        }
        return headers;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatePaymentRequest {
        public Amount amount;
        public String description;
        public boolean capture = true;
        public Confirmation confirmation;
        public Receipt receipt;
        public Metadata metadata;
    }

    public static class CreatePaymentResponse {
        public String id;
        public String status;
        public Amount amount;
        public Confirmation confirmation;
        public Metadata metadata;
    }

    public static class Amount {
        public String value;
        public String currency;

        public static Amount of(String value, String currency) {
            Amount amount = new Amount();
            amount.value = value;
            amount.currency = currency;
            return amount;
        }
    }

    public static class Confirmation {
        public String type;
        @JsonProperty("return_url")
        public String returnUrl;
        @JsonProperty("confirmation_url")
        public String confirmationUrl;

        public static Confirmation redirect(String returnUrl) {
            Confirmation confirmation = new Confirmation();
            confirmation.type = "redirect";
            confirmation.returnUrl = returnUrl;
            return confirmation;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Receipt {
        public ReceiptCustomer customer;
        @JsonProperty("tax_system_code")
        public Integer taxSystemCode;
        public List<ReceiptItem> items;
    }

    public static class ReceiptCustomer {
        public String email;
        public String phone;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReceiptItem {
        public String description;
        public BigDecimal quantity;
        public Amount amount;
        @JsonProperty("vat_code")
        public Integer vatCode;
        @JsonProperty("payment_mode")
        public String paymentMode;
        @JsonProperty("payment_subject")
        public String paymentSubject;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Metadata {
        @JsonProperty("order_id")
        public String orderId;
        @JsonProperty("public_token")
        public String publicToken;

        public static Metadata of(String orderId, String publicToken) {
            Metadata metadata = new Metadata();
            metadata.orderId = orderId;
            metadata.publicToken = publicToken;
            return metadata;
        }
    }
}
