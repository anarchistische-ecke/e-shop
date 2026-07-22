package com.example.payment.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class YooKassaLiveSmokeTest {

    private static final String TEST_CARD_NO_3DS = "5555555555554444";

    @Test
    void liveTestContourPaymentReceiptAndPartialRefundReceipt() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_YOOKASSA_LIVE_TESTS")),
                "Set RUN_YOOKASSA_LIVE_TESTS=true to run the YooKassa test-contour smoke test.");
        String shopId = envOrDefault("YOOKASSA_SHOP_ID", "1348143");
        String secretKey = System.getenv("YOOKASSA_SECRET_KEY");
        Assumptions.assumeTrue(StringUtils.hasText(shopId), "YOOKASSA_SHOP_ID is required.");
        Assumptions.assumeTrue(StringUtils.hasText(secretKey), "YOOKASSA_SECRET_KEY is required.");

        YooKassaClient client = new YooKassaClient(new RestTemplate());
        ReflectionTestUtils.setField(client, "baseUrl", envOrDefault("YOOKASSA_BASE_URL", "https://api.yookassa.ru/v3"));
        ReflectionTestUtils.setField(client, "shopId", shopId);
        ReflectionTestUtils.setField(client, "secretKey", secretKey);

        String publicToken = UUID.randomUUID().toString();
        YooKassaClient.CreatePaymentRequest paymentRequest = new YooKassaClient.CreatePaymentRequest();
        paymentRequest.amount = YooKassaClient.Amount.of("2.00", "RUB");
        paymentRequest.capture = true;
        paymentRequest.description = "YooKassa live smoke " + publicToken;
        paymentRequest.paymentMethodData = YooKassaClient.PaymentMethodData.bankCard(
                YooKassaClient.BankCardData.of(TEST_CARD_NO_3DS, "2030", "12", "123", "YOO TEST")
        );
        paymentRequest.confirmation = YooKassaClient.Confirmation.redirect("https://example.test/yookassa-return");
        paymentRequest.metadata = YooKassaClient.Metadata.of(publicToken, publicToken);
        paymentRequest.receipt = receipt("YooKassa smoke item", "2.00");

        YooKassaClient.CreatePaymentResponse payment = createPayment(client, paymentRequest, "live-payment-" + publicToken);
        YooKassaClient.CreatePaymentResponse current = waitForTerminalPaymentStatus(client, payment.id);
        String paymentReceiptRegistration = firstText(payment.receiptRegistration, current.receiptRegistration);
        YooKassaClient.PaymentMethod paymentMethod = current.paymentMethod != null
                ? current.paymentMethod
                : payment.paymentMethod;

        assertThat(current.id).isEqualTo(payment.id);
        assertThat(Boolean.TRUE.equals(current.test) || Boolean.TRUE.equals(payment.test))
                .as("YooKassa must create this payment in test contour")
                .isTrue();
        assertThat(paymentReceiptRegistration)
                .as("Receipt registration must be present for payment %s (status %s). Check the YooKassa test shop Online-Kassa settings if this fails.",
                        current.id,
                        current.status)
                .isIn("pending", "succeeded");
        assertThat(paymentMethod).isNotNull();
        assertThat(paymentMethod.type).isEqualTo("bank_card");
        assertThat(paymentMethod.card).isNotNull();
        assertThat(paymentMethod.card.first6).isEqualTo("555555");
        assertThat(paymentMethod.card.last4).isEqualTo("4444");

        Assumptions.assumeTrue("succeeded".equalsIgnoreCase(current.status),
                "Payment did not finish synchronously; partial refund smoke is skipped for status " + current.status + ".");

        YooKassaClient.CreateRefundRequest refundRequest = new YooKassaClient.CreateRefundRequest();
        refundRequest.paymentId = current.id;
        refundRequest.amount = YooKassaClient.Amount.of("1.00", "RUB");
        refundRequest.metadata = YooKassaClient.Metadata.of(publicToken, publicToken);
        refundRequest.receipt = receipt("YooKassa smoke partial refund", "1.00");

        YooKassaClient.RefundResponse refund = createRefund(client, refundRequest, "live-refund-" + publicToken);
        YooKassaClient.RefundResponse currentRefund = waitForTerminalRefundStatus(client, refund.id);
        String refundReceiptRegistration = firstText(refund.receiptRegistration, currentRefund.receiptRegistration);
        assertThat(refund.id).isNotBlank();
        assertThat(currentRefund.paymentId).isEqualTo(current.id);
        assertThat(currentRefund.status.toLowerCase(Locale.ROOT)).isIn("pending", "succeeded");
        assertThat(refundReceiptRegistration)
                .as("Refund receipt registration must be present for refund %s (status %s). Check the YooKassa test shop Online-Kassa settings if this fails.",
                        currentRefund.id,
                        currentRefund.status)
                .isIn("pending", "succeeded");
    }

    private YooKassaClient.CreatePaymentResponse createPayment(YooKassaClient client,
                                                              YooKassaClient.CreatePaymentRequest request,
                                                              String idempotencyKey) {
        try {
            YooKassaClient.CreatePaymentResponse response = client.createPayment(request, idempotencyKey);
            assertThat(response).isNotNull();
            assertThat(response.id).isNotBlank();
            return response;
        } catch (RestClientResponseException error) {
            fail("YooKassa test-contour payment creation failed. Verify test shop credentials, test-card support, and receipt settings. Response: "
                    + error.getResponseBodyAsString());
            throw error;
        }
    }

    private YooKassaClient.RefundResponse createRefund(YooKassaClient client,
                                                       YooKassaClient.CreateRefundRequest request,
                                                       String idempotencyKey) {
        try {
            YooKassaClient.RefundResponse response = client.createRefund(request, idempotencyKey);
            assertThat(response).isNotNull();
            return response;
        } catch (RestClientResponseException error) {
            fail("YooKassa test-contour refund creation failed. Verify test shop refund and receipt settings. Response: "
                    + error.getResponseBodyAsString());
            throw error;
        }
    }

    private YooKassaClient.CreatePaymentResponse waitForTerminalPaymentStatus(YooKassaClient client,
                                                                             String paymentId) throws InterruptedException {
        YooKassaClient.CreatePaymentResponse current = client.getPayment(paymentId);
        for (int attempt = 0; attempt < 10 && isPending(current); attempt++) {
            Thread.sleep(1_000L);
            current = client.getPayment(paymentId);
        }
        return current;
    }

    private YooKassaClient.RefundResponse waitForTerminalRefundStatus(YooKassaClient client,
                                                                      String refundId) throws InterruptedException {
        YooKassaClient.RefundResponse current = client.getRefund(refundId);
        for (int attempt = 0; attempt < 10 && current != null && "pending".equalsIgnoreCase(current.status); attempt++) {
            Thread.sleep(1_000L);
            current = client.getRefund(refundId);
        }
        return current;
    }

    private boolean isPending(YooKassaClient.CreatePaymentResponse payment) {
        return payment == null
                || payment.status == null
                || "pending".equalsIgnoreCase(payment.status)
                || "waiting_for_capture".equalsIgnoreCase(payment.status);
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }

    private YooKassaClient.Receipt receipt(String description, String value) {
        YooKassaClient.Receipt receipt = new YooKassaClient.Receipt();
        receipt.customer = new YooKassaClient.ReceiptCustomer();
        receipt.customer.email = "yookassa-smoke@example.test";
        receipt.taxSystemCode = 1;

        YooKassaClient.ReceiptItem item = new YooKassaClient.ReceiptItem();
        item.description = description;
        item.quantity = BigDecimal.ONE;
        item.amount = YooKassaClient.Amount.of(value, "RUB");
        item.vatCode = 1;
        item.paymentMode = "full_prepayment";
        item.paymentSubject = "commodity";
        receipt.items = List.of(item);
        return receipt;
    }

    private String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
