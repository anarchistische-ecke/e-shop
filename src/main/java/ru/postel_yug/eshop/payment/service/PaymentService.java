package ru.postel_yug.eshop.payment.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import ru.postel_yug.eshop.order.dto.PaymentDetails;
import ru.postel_yug.eshop.order.entity.Order;
import ru.postel_yug.eshop.order.entity.OrderStatus;
import ru.postel_yug.eshop.payment.dto.LifepayCallbackData;
import ru.postel_yug.eshop.payment.entity.PaymentTransaction;
import ru.postel_yug.eshop.payment.exception.PaymentFailedException;
import ru.postel_yug.eshop.payment.repository.PaymentRepository;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {
    @Autowired
    private WebClient lifepayWebClient;
    @Autowired private PaymentRepository paymentRepo;
    @Value("${app.lifepay.callbackUrl}")
    private String callbackUrl;

    public void processPayment(Order order, PaymentDetails paymentDetails) throws PaymentFailedException {
        // Создаем запись о платеже в статусе INIT
        PaymentTransaction pt = new PaymentTransaction();
        pt.setOrder(order);
        pt.setAmount(order.getTotalPrice().add(order.getShippingPrice()).subtract(order.getTotalDiscount()));
        pt.setStatus("INITIATED");
        pt.setCreatedAt(LocalDateTime.now());
        paymentRepo.save(pt);
        // Подготовка запроса к Lifepay API
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("orderId", order.getId().toString());
        requestBody.put("amount", pt.getAmount());
        requestBody.put("currency", "RUB");
        requestBody.put("description", "Order #" + order.getId());
        if(paymentDetails.getType().equals("card") && paymentDetails.getCardToken() != null) {
            requestBody.put("cardToken", paymentDetails.getCardToken());
        }
        // указать URL для callback, если Lifepay требует:
        requestBody.put("callbackUrl", callbackUrl);
        try {
            // Отправляем запрос на платеж
            Map<String, Object> response = lifepayWebClient.post()
                    .uri("/payments")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new PaymentFailedException("Lifepay error: " + errorBody))))
                    .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
                    .block(Duration.ofSeconds(10));
            if(response == null) {
                throw new PaymentFailedException("Нет ответа от платежного сервиса");
            }
            String status = (String) response.get("status");
            String transactionId = (String) response.get("transactionId");
            pt.setTransactionId(transactionId);
            pt.setStatus(status);
            pt.setUpdatedAt(LocalDateTime.now());
            paymentRepo.save(pt);
            if("SUCCESS".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
                // Платеж завершен успешно
                return;
            } else if("PROCESSING".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
                // Платеж в процессе (например, 3DSecure)
                // Решение: либо ждать webhook, либо сделать паузу и попытку проверки статуса.
                // Пока считаем, что webhook придет и обновит статус позже.
                return;
            } else {
                // Любой другой статус считаем неуспехом
                throw new PaymentFailedException("Статус платежа: " + status);
            }
        } catch(WebClientRequestException e) {
            // timeout or connection error
            throw new PaymentFailedException("Не удалось соединиться с платежным шлюзом");
        }
    }

    public void handleCallback(LifepayCallbackData callback) {
        // Этот метод вызывается из контроллера при получении вебхука от Lifepay
        PaymentTransaction pt = paymentRepo.findByTransactionId(callback.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        pt.setStatus(callback.getStatus());
        pt.setUpdatedAt(LocalDateTime.now());
        paymentRepo.save(pt);
        if("SUCCESS".equalsIgnoreCase(callback.getStatus())) {
            // Найдем связанный заказ и обновим его статус, если нужно
            Order order = pt.getOrder();
            if(order.getStatus() != OrderStatus.PAID) {
                // Отметим как оплаченный
                order.setStatus(OrderStatus.PAID);
                order.setPaidAt(LocalDateTime.now());
                // Сохранить через orderRepo (можно Autowire OrderRepository или OrderService)
            }
        }
        // Если статус FAIL, можно аналогично пометить заказ отмененным, если еще не был.
    }
}

