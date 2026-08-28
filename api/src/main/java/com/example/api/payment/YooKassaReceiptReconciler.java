package com.example.api.payment;

import com.example.payment.domain.PaymentStatus;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(
        prefix = "yookassa",
        name = "enabled",
        havingValue = "true"
)
@ConditionalOnProperty(
        prefix = "yookassa.receipt-reconciliation",
        name = "enabled",
        havingValue = "true"
)
public class YooKassaReceiptReconciler {
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final int batchSize;

    public YooKassaReceiptReconciler(PaymentRepository paymentRepository,
                                     PaymentService paymentService,
                                     @Value("${yookassa.receipt-reconciliation.batch-size:50}") int batchSize) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${yookassa.receipt-reconciliation.fixed-delay-ms:60000}")
    public void scheduledReconcile() {
        reconcilePendingReceipts();
    }

    public int reconcilePendingReceipts() {
        List<String> paymentIds = paymentRepository.findProviderPaymentIdsWithPendingReceipt(
                PaymentStatus.COMPLETED,
                PageRequest.of(0, batchSize)
        );
        int reconciled = 0;
        for (String paymentId : paymentIds) {
            if (paymentService.reconcileYooKassaPayment(paymentId) != null) {
                reconciled++;
            }
        }
        return reconciled;
    }
}
