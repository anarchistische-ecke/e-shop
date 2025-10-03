package ru.postel_yug.eshop.payment.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.postel_yug.eshop.payment.dto.LifepayCallbackData;
import ru.postel_yug.eshop.payment.service.PaymentService;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {
    @Autowired
    private PaymentService paymentService;

    @PostMapping("/callback")
    public ResponseEntity<String> lifepayCallback(@RequestBody LifepayCallbackData callback) {
        try {
            paymentService.handleCallback(callback);
            return ResponseEntity.ok("OK");
        } catch(Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("error");
        }
    }
}

