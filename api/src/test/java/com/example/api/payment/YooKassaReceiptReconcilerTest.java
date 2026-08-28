package com.example.api.payment;

import com.example.common.domain.Money;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YooKassaReceiptReconcilerTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentService paymentService;

    @Test
    void reconcilesCompletedPaymentsWhoseReceiptIsStillPending() {
        String firstId = "yookassa-payment-1";
        String secondId = "yookassa-payment-2";
        Payment payment = new Payment(UUID.randomUUID(), Money.of(420000, "RUB"), "YOOKASSA", PaymentStatus.COMPLETED);
        YooKassaReceiptReconciler reconciler = new YooKassaReceiptReconciler(
                paymentRepository,
                paymentService,
                2
        );

        when(paymentRepository.findProviderPaymentIdsWithPendingReceipt(eq(PaymentStatus.COMPLETED),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(firstId, secondId));
        when(paymentService.reconcileYooKassaPayment(firstId))
                .thenReturn(new PaymentService.PaymentUpdateResult(payment, false));
        when(paymentService.reconcileYooKassaPayment(secondId)).thenReturn(null);

        int reconciled = reconciler.reconcilePendingReceipts();

        assertThat(reconciled).isEqualTo(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findProviderPaymentIdsWithPendingReceipt(
                eq(PaymentStatus.COMPLETED),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        verify(paymentService).reconcileYooKassaPayment(firstId);
        verify(paymentService).reconcileYooKassaPayment(secondId);
    }
}
