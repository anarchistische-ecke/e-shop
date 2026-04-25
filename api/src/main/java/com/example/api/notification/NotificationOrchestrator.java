package com.example.api.notification;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.payment.domain.Payment;
import com.example.shipment.domain.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrator.class);

    private final NotificationService notificationService;
    private final NotificationProperties properties;
    private final NotificationTemplateService templateService;

    public NotificationOrchestrator(NotificationService notificationService,
                                    NotificationProperties properties,
                                    NotificationTemplateService templateService) {
        this.notificationService = notificationService;
        this.properties = properties;
        this.templateService = templateService;
    }

    public void orderPaid(Order order, Payment payment) {
        if (order == null) {
            return;
        }
        Map<String, Object> payload = orderPayload(order);
        payload.put("providerPaymentId", payment != null ? payment.getProviderPaymentId() : null);
        payload.put("receiptRegistration", payment != null ? payment.getReceiptRegistration() : null);
        payload.put("receiptUrl", payment != null ? payment.getReceiptUrl() : null);
        enqueue(
                NotificationType.ORDER_PAID,
                eventKey("ORDER_PAID", order.getId(), payment != null ? payment.getProviderPaymentId() : null),
                "ORDER",
                order.getId(),
                order.getReceiptEmail(),
                payload
        );
    }

    public void orderShipped(Order order, Shipment shipment) {
        if (order == null || shipment == null) {
            return;
        }
        Map<String, Object> payload = orderPayload(order);
        payload.put("carrier", shipment.getCarrier());
        payload.put("trackingNumber", shipment.getTrackingNumber());
        payload.put("trackingUrl", trackingUrl(shipment.getCarrier(), shipment.getTrackingNumber()));
        enqueue(
                NotificationType.ORDER_SHIPPED,
                eventKey("ORDER_SHIPPED", shipment.getId(), null),
                "ORDER",
                order.getId(),
                order.getReceiptEmail(),
                payload
        );
    }

    public void orderStatusChanged(Order order, String previousStatus) {
        if (order == null || !statusChanged(previousStatus, order.getStatus())) {
            return;
        }
        String status = normalizeStatus(order.getStatus());
        if ("DELIVERED".equals(status)) {
            orderDelivered(order);
        } else if ("RECEIVED".equals(status) || "COMPLETED".equals(status)) {
            orderReceived(order);
        }
    }

    public void orderDelivered(Order order) {
        if (order == null) {
            return;
        }
        Map<String, Object> payload = orderPayload(order);
        payload.put("status", "DELIVERED");
        payload.put("statusLabel", templateService.statusLabel("DELIVERED"));
        enqueue(
                NotificationType.ORDER_DELIVERED,
                eventKey("ORDER_DELIVERED", order.getId(), null),
                "ORDER",
                order.getId(),
                order.getReceiptEmail(),
                payload
        );
    }

    public void orderReceived(Order order) {
        if (order == null) {
            return;
        }
        Map<String, Object> payload = orderPayload(order);
        payload.put("status", "RECEIVED");
        payload.put("statusLabel", templateService.statusLabel("RECEIVED"));
        enqueue(
                NotificationType.ORDER_RECEIVED,
                eventKey("ORDER_RECEIVED", order.getId(), null),
                "ORDER",
                order.getId(),
                order.getReceiptEmail(),
                payload
        );
    }

    public void rmaDecision(Order order,
                            UUID rmaId,
                            String rmaNumber,
                            int decisionVersion,
                            String decisionStatus,
                            String managerComment) {
        if (order == null || rmaId == null) {
            return;
        }
        Map<String, Object> payload = orderPayload(order);
        payload.put("rmaId", rmaId);
        payload.put("rmaNumber", rmaNumber);
        payload.put("decisionStatus", decisionStatus);
        payload.put("decisionStatusLabel", templateService.rmaDecisionLabel(decisionStatus));
        payload.put("managerComment", managerComment);
        enqueue(
                NotificationType.RMA_DECISION,
                "RMA_DECISION:" + rmaId + ":" + Math.max(1, decisionVersion),
                "RMA_REQUEST",
                rmaId,
                order.getReceiptEmail(),
                payload
        );
    }

    private void enqueue(NotificationType type,
                         String eventKey,
                         String aggregateType,
                         UUID aggregateId,
                         String recipient,
                         Map<String, Object> payload) {
        try {
            notificationService.enqueueOnce(type, eventKey, aggregateType, aggregateId, recipient, payload);
        } catch (RuntimeException ex) {
            log.warn("Failed to enqueue {} notification for {}", type, eventKey, ex);
        }
    }

    private Map<String, Object> orderPayload(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNumber", order.getId());
        payload.put("status", order.getStatus());
        payload.put("statusLabel", templateService.statusLabel(order.getStatus()));
        payload.put("recipient", order.getReceiptEmail());
        payload.put("amount", formatMoneyAmount(order.getTotalAmount()));
        payload.put("currency", currency(order.getTotalAmount()));
        payload.put("amountFormatted", formatMoney(order.getTotalAmount()));
        payload.put("items", orderItems(order));
        return payload;
    }

    private List<Map<String, Object>> orderItems(Order order) {
        if (order.getItems() == null) {
            return List.of();
        }
        return order.getItems().stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.getId())))
                .map(item -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", item.getId());
                    view.put("name", itemName(item));
                    view.put("productName", item.getProductName());
                    view.put("variantName", item.getVariantName());
                    view.put("sku", item.getSku());
                    view.put("quantity", item.getQuantity());
                    view.put("unitAmount", formatMoneyAmount(item.getUnitPrice()));
                    view.put("currency", currency(item.getUnitPrice()));
                    view.put("unitAmountFormatted", formatMoney(item.getUnitPrice()));
                    return view;
                })
                .toList();
    }

    private String itemName(OrderItem item) {
        String product = StringUtils.hasText(item.getProductName()) ? item.getProductName() : "Товар";
        String variant = StringUtils.hasText(item.getVariantName()) ? " (" + item.getVariantName() + ")" : "";
        return product + variant;
    }

    private String trackingUrl(String carrier, String trackingNumber) {
        if (!StringUtils.hasText(carrier) || !StringUtils.hasText(trackingNumber)) {
            return null;
        }
        String template = resolveTrackingTemplate(carrier);
        if (!StringUtils.hasText(template)) {
            return null;
        }
        String encoded = UriUtils.encodePathSegment(trackingNumber.trim(), StandardCharsets.UTF_8);
        return template
                .replace("{trackingNumber}", encoded)
                .replace("{tracking_number}", encoded);
    }

    private String resolveTrackingTemplate(String carrier) {
        String normalized = carrier.trim().toLowerCase(Locale.ROOT);
        Map<String, String> templates = properties.getTrackingUrlTemplates();
        String exact = templates.get(normalized);
        if (StringUtils.hasText(exact)) {
            return exact;
        }
        return templates.entrySet().stream()
                .filter(entry -> normalized.equals(entry.getKey().trim().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String eventKey(String prefix, UUID id, String suffix) {
        return StringUtils.hasText(suffix)
                ? prefix + ":" + id + ":" + suffix.trim()
                : prefix + ":" + id;
    }

    private boolean statusChanged(String previousStatus, String nextStatus) {
        String previous = normalizeStatus(previousStatus);
        String next = normalizeStatus(nextStatus);
        return StringUtils.hasText(next) && !previous.equals(next);
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String currency(Money money) {
        return money != null && StringUtils.hasText(money.getCurrency()) ? money.getCurrency() : "RUB";
    }

    private String formatMoney(Money money) {
        return formatMoneyAmount(money) + " " + currency(money);
    }

    private String formatMoneyAmount(Money money) {
        if (money == null) {
            return "0.00";
        }
        BigDecimal value = money.toBigDecimal();
        return value.setScale(2, RoundingMode.HALF_UP).toString();
    }
}
