package ru.postel_yug.eshop.shipping.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.postel_yug.eshop.order.entity.Order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShippingService {
    @Autowired
    private WebClient cdekWebClient;
    @Autowired private CdekAuthService cdekAuthService;

    // Рассчитать стоимость доставки
    public BigDecimal calculateShippingCost(String city, String deliveryType, BigDecimal orderAmount) {
        // Упрощенно: если сумма превышает порог, бесплатная доставка
        if(orderAmount.compareTo(new BigDecimal("5000")) >= 0) {
            return BigDecimal.ZERO;
        }
        // Иначе фиксированная цена по типу
        if("CDEK_COURIER".equals(deliveryType)) {
            return new BigDecimal("300.00"); // например, 300 руб
        } else if("CDEK_PICKUP".equals(deliveryType)) {
            return new BigDecimal("150.00"); // дешевле до ПВЗ
        }
        // Можно использовать API CDEK калькулятора:
        // Но для этого нужен вес/габариты, которых у нас нет.
        // Можно задать дефолт вес, например 1 kg.
        return new BigDecimal("300.00");
    }

    // Создать отправление в системе CDEK, вернуть трек-номер
    public String createShipment(Order order, String city, String address) {
        // Собираем данные для запроса
        Map<String, Object> request = new HashMap<>();
        // example structure:
        Map<String, Object> sender = Map.of("city_code", 44);   // Москва, например
        Map<String, Object> recipient;
        if(address != null && !address.isBlank()) {
            // Курьерская доставка
            recipient = Map.of(
                    "city", city,
                    "address", address,
                    "name", order.getUser().getName(),
                    "phone", order.getUser().getEmail() // предположим, email, нет, лучше телефон, но если нет, оставим email
            );
        } else {
            // Самовывоз - нужно указать код ПВЗ, но у нас нет, предположим city suffices
            recipient = Map.of("city", city);
        }
        List<Map<String,Object>> packages = new ArrayList<>();
        // Приложим одну посылку с суммарным весом
        packages.add(Map.of(
                "weight", 1000,    // 1000 грамм
                "length", 10, "width", 10, "height", 10,
                "items", order.getItems().stream().map(item -> Map.of(
                        "name", item.getProductName(),
                        "ware_key", item.getProductId().toString(),
                        "cost", item.getPrice().intValue(),  // int рублей, предполагаем price без копеек
                        "amount", item.getQuantity()
                )).collect(Collectors.toList())
        ));
        request.put("sender", sender);
        request.put("recipient", recipient);
        request.put("packages", packages);
        request.put("tariff_code", deliveryType.equals("CDEK_COURIER") ? 139 : 136);
        // 139 - тариф Курьерская доставка, 136 - до пункта выдачи (пример)
        // Выполняем запрос
        try {
            Map<String, Object> response = cdekWebClient.post()
                    .uri("/v2/orders")
                    .header("Authorization", "Bearer " + cdekAuthService.getToken())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
                    .block();
            if(response == null) {
                throw new RuntimeException("CDEK create order no response");
            }
            // CDEK возвращает ID заказа их системы
            String cdekOrderId = (String) response.get("entity") != null ? ((Map<String,Object>)response.get("entity")).get("uuid").toString() : null;
            // Трек-номер может быть не сразу, но часто совпадает с uuid или отдельным полем.
            String trackingNumber = response.containsKey("tracking_number") ? response.get("tracking_number").toString() : cdekOrderId;
            return trackingNumber;
        } catch(WebClientResponseException e) {
            // Логируем ошибку
            System.err.println("Ошибка CDEK: " + e.getResponseBodyAsString());
            throw new RuntimeException("Ошибка отправки в CDEK");
        }
    }
}

