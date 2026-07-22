package com.example.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class YooKassaClientHttpTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;
    private YooKassaClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        client = new YooKassaClient(new RestTemplate());
        ReflectionTestUtils.setField(client, "baseUrl", server.url("/v3").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(client, "shopId", "1348143");
        ReflectionTestUtils.setField(client, "secretKey", "test_secret");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void createPaymentSendsReceiptFiscalFieldsIdempotenceKeyAndBasicAuth() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "pay-test-1",
                          "status": "pending",
                          "paid": false,
                          "test": true,
                          "amount": {"value": "112.34", "currency": "RUB"},
                          "receipt_registration": "pending",
                          "confirmation": {"type": "redirect", "confirmation_url": "https://yookassa.test/confirm"}
                        }
                        """));

        YooKassaClient.CreatePaymentRequest request = new YooKassaClient.CreatePaymentRequest();
        request.amount = YooKassaClient.Amount.of("112.34", "RUB");
        request.capture = true;
        request.description = "Order order-1";
        request.confirmation = YooKassaClient.Confirmation.redirect("https://example.test/return");
        request.receipt = receipt(6, 2);

        YooKassaClient.CreatePaymentResponse response = client.createPayment(request, "idem-key-1");

        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/v3/payments");
        assertThat(recorded.getHeader("Idempotence-Key")).isEqualTo("idem-key-1");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("1348143:test_secret".getBytes(StandardCharsets.UTF_8)));

        JsonNode json = objectMapper.readTree(recorded.getBody().readUtf8());
        assertThat(json.at("/receipt/customer/email").asText()).isEqualTo("buyer@example.test");
        assertThat(json.at("/receipt/customer/phone").asText()).isEqualTo("+79990000000");
        assertThat(json.at("/receipt/tax_system_code").asInt()).isEqualTo(6);
        assertThat(json.at("/receipt/items/0/description").asText()).isEqualTo("Комплект Linen");
        assertThat(json.at("/receipt/items/0/amount/value").asText()).isEqualTo("100.00");
        assertThat(json.at("/receipt/items/0/vat_code").asInt()).isEqualTo(2);
        assertThat(json.at("/receipt/items/0/payment_mode").asText()).isEqualTo("full_prepayment");
        assertThat(json.at("/receipt/items/0/payment_subject").asText()).isEqualTo("commodity");
        assertThat(json.at("/receipt/items/1/payment_subject").asText()).isEqualTo("service");

        assertThat(response.id).isEqualTo("pay-test-1");
        assertThat(response.test).isTrue();
        assertThat(response.paid).isFalse();
        assertThat(response.receiptRegistration).isEqualTo("pending");
    }

    @Test
    void createPaymentCanSerializeTestBankCardDataForGatedLiveSmoke() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "pay-card-test-1",
                          "status": "succeeded",
                          "paid": true,
                          "test": true,
                          "payment_method": {
                            "id": "pm-1",
                            "type": "bank_card",
                            "saved": false,
                            "card": {"first6": "555555", "last4": "4444", "card_type": "MasterCard"}
                          }
                        }
                        """));

        YooKassaClient.CreatePaymentRequest request = new YooKassaClient.CreatePaymentRequest();
        request.amount = YooKassaClient.Amount.of("2.00", "RUB");
        request.capture = true;
        request.paymentMethodData = YooKassaClient.PaymentMethodData.bankCard(
                YooKassaClient.BankCardData.of("5555555555554444", "2030", "12", "123", "YOO TEST")
        );
        request.receipt = receipt(1, 1);

        YooKassaClient.CreatePaymentResponse response = client.createPayment(request, "idem-card");
        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);

        JsonNode json = objectMapper.readTree(recorded.getBody().readUtf8());
        assertThat(json.at("/payment_method_data/type").asText()).isEqualTo("bank_card");
        assertThat(json.at("/payment_method_data/card/number").asText()).isEqualTo("5555555555554444");
        assertThat(json.at("/payment_method_data/card/expiry_year").asText()).isEqualTo("2030");
        assertThat(json.at("/payment_method_data/card/expiry_month").asText()).isEqualTo("12");
        assertThat(json.at("/payment_method_data/card/csc").asText()).isEqualTo("123");
        assertThat(response.paymentMethod.card.first6).isEqualTo("555555");
        assertThat(response.paymentMethod.card.last4).isEqualTo("4444");
    }

    @Test
    void createRefundDeserializesReceiptRegistration() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "refund-1",
                          "status": "succeeded",
                          "test": true,
                          "payment_id": "pay-test-1",
                          "amount": {"value": "1.00", "currency": "RUB"},
                          "receipt_registration": "pending"
                        }
                        """));

        YooKassaClient.CreateRefundRequest request = new YooKassaClient.CreateRefundRequest();
        request.paymentId = "pay-test-1";
        request.amount = YooKassaClient.Amount.of("1.00", "RUB");
        request.receipt = receipt(1, 1);

        YooKassaClient.RefundResponse response = client.createRefund(request, "refund-idem");

        assertThat(response.id).isEqualTo("refund-1");
        assertThat(response.test).isTrue();
        assertThat(response.receiptRegistration).isEqualTo("pending");
    }

    private YooKassaClient.Receipt receipt(int taxSystemCode, int vatCode) {
        YooKassaClient.Receipt receipt = new YooKassaClient.Receipt();
        receipt.customer = new YooKassaClient.ReceiptCustomer();
        receipt.customer.email = "buyer@example.test";
        receipt.customer.phone = "+79990000000";
        receipt.taxSystemCode = taxSystemCode;

        YooKassaClient.ReceiptItem item = new YooKassaClient.ReceiptItem();
        item.description = "Комплект Linen";
        item.quantity = BigDecimal.ONE;
        item.amount = YooKassaClient.Amount.of("100.00", "RUB");
        item.vatCode = vatCode;
        item.paymentMode = "full_prepayment";
        item.paymentSubject = "commodity";

        YooKassaClient.ReceiptItem delivery = new YooKassaClient.ReceiptItem();
        delivery.description = "Доставка";
        delivery.quantity = BigDecimal.ONE;
        delivery.amount = YooKassaClient.Amount.of("12.34", "RUB");
        delivery.vatCode = vatCode;
        delivery.paymentMode = "full_prepayment";
        delivery.paymentSubject = "service";

        receipt.items = List.of(item, delivery);
        return receipt;
    }
}
