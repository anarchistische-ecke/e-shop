package com.example.api.payment;

import com.example.payment.domain.Payment;
import com.example.payment.service.PaymentService;
import com.example.common.domain.Money;
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
    public ResponseEntity<Payment> processPayment(@Valid @RequestBody PaymentRequest request) {
        Money amount = Money.of(request.getAmount(), request.getCurrency());
        Payment payment = paymentService.processPayment(request.getOrderId(), amount, request.getMethod());
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
        public UUID getOrderId() { return orderId; }
        public void setOrderId(UUID orderId) { this.orderId = orderId; }
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
    }
}

