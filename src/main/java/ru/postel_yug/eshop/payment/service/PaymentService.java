package ru.postel_yug.eshop.payment.service;

import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.payment.exception.PaymentFailedException;

@Service
public class PaymentService {

    public boolean processPayment(Long userId, double amount, String paymentMethod) {
        try {
            // обращение к лайф пэю ?????????????????????????????????????????
            return true;
        } catch (Exception ex) {
            throw new PaymentFailedException("Payment failed", ex);
        }
    }
}
