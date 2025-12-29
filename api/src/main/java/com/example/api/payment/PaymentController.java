package com.example.api.payment;

import com.example.payment.domain.Payment;
import com.example.payment.service.PaymentService;
import com.example.payment.service.BrowserInfo;
import com.example.common.domain.Money;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Payment> processPayment(@Valid @RequestBody PaymentRequest request,
                                                  HttpServletRequest httpRequest) {
        Money amount = Money.of(request.getAmount(), request.getCurrency());
        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        String acceptHdr = httpRequest.getHeader("Accept");
        String language = httpRequest.getHeader("Accept-Language");
        if (language == null || language.isEmpty()) {
            language = "en-US";
        }
        int screenWidth = (request.getScreenWidth() != null ? request.getScreenWidth() : 1920);
        int screenHeight = (request.getScreenHeight() != null ? request.getScreenHeight() : 1080);
        int colorDepth = (request.getColorDepth() != null ? request.getColorDepth() : 24);
        int timezoneOffset = (request.getTimezoneOffset() != null ? request.getTimezoneOffset() : 0);
        boolean jsEnabled = (request.getJavascriptEnabled() != null ? request.getJavascriptEnabled() : true);
        boolean javaEnabled = (request.getJavaEnabled() != null ? request.getJavaEnabled() : false);

        BrowserInfo browserInfo = new BrowserInfo(
                ip,
                (acceptHdr != null ? acceptHdr : "*/*"),
                jsEnabled,
                language,
                screenHeight,
                screenWidth,
                timezoneOffset,
                (userAgent != null ? userAgent : ""),
                javaEnabled,
                colorDepth
        );

        String cardToken = request.getCardToken();
        if ((cardToken == null || cardToken.isEmpty()) && request.getCardNumber() != null) {
            // In a real scenario, we should not be here because card should be tokenized on frontend.
            // We could encrypt card details with LifePay's public key to create a token, but that is omitted for brevity.
            // Throw an error or handle accordingly:
            throw new IllegalArgumentException("Card token not provided. Please tokenize card details.");
        }

        Payment payment = paymentService.processPayment(
                request.getOrderId(), amount, request.getMethod(), cardToken, browserInfo);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    public static class PaymentRequest {
        @NotNull
        private UUID orderId;
        @NotNull
        private Long amount;
        @NotBlank
        private String currency;
        @NotBlank
        private String method;
        private String cardNumber;
        private String cardExpiryMonth;
        private String cardExpiryYear;
        private String cardCvv;
        private String cardHolderName;
        private String cardToken;
        private Integer screenWidth;
        private Integer screenHeight;
        private Integer colorDepth;
        private Integer timezoneOffset;
        private Boolean javascriptEnabled;
        private Boolean javaEnabled;

        public UUID getOrderId() {
            return orderId;
        }

        public void setOrderId(UUID orderId) {
            this.orderId = orderId;
        }

        public Long getAmount() {
            return amount;
        }

        public void setAmount(Long amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getCardNumber() {
            return cardNumber;
        }

        public void setCardNumber(String cardNumber) {
            this.cardNumber = cardNumber;
        }

        public String getCardExpiryMonth() {
            return cardExpiryMonth;
        }

        public void setCardExpiryMonth(String cardExpiryMonth) {
            this.cardExpiryMonth = cardExpiryMonth;
        }

        public String getCardExpiryYear() {
            return cardExpiryYear;
        }

        public void setCardExpiryYear(String cardExpiryYear) {
            this.cardExpiryYear = cardExpiryYear;
        }

        public String getCardCvv() {
            return cardCvv;
        }

        public void setCardCvv(String cardCvv) {
            this.cardCvv = cardCvv;
        }

        public String getCardHolderName() {
            return cardHolderName;
        }

        public void setCardHolderName(String cardHolderName) {
            this.cardHolderName = cardHolderName;
        }

        public String getCardToken() {
            return cardToken;
        }

        public void setCardToken(String cardToken) {
            this.cardToken = cardToken;
        }

        public Integer getScreenWidth() {
            return screenWidth;
        }

        public void setScreenWidth(Integer screenWidth) {
            this.screenWidth = screenWidth;
        }

        public Integer getScreenHeight() {
            return screenHeight;
        }

        public void setScreenHeight(Integer screenHeight) {
            this.screenHeight = screenHeight;
        }

        public Integer getColorDepth() {
            return colorDepth;
        }

        public void setColorDepth(Integer colorDepth) {
            this.colorDepth = colorDepth;
        }

        public Integer getTimezoneOffset() {
            return timezoneOffset;
        }

        public void setTimezoneOffset(Integer timezoneOffset) {
            this.timezoneOffset = timezoneOffset;
        }

        public Boolean getJavascriptEnabled() {
            return javascriptEnabled;
        }

        public void setJavascriptEnabled(Boolean javascriptEnabled) {
            this.javascriptEnabled = javascriptEnabled;
        }

        public Boolean getJavaEnabled() {
            return javaEnabled;
        }

        public void setJavaEnabled(Boolean javaEnabled) {
            this.javaEnabled = javaEnabled;
        }
    }
}
