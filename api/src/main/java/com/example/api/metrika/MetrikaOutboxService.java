package com.example.api.metrika;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
public class MetrikaOutboxService {
    public static final String TARGET_ORDER_CREATED = "order_created";
    public static final String TARGET_PURCHASE_PAID = "purchase_paid";
    public static final String TARGET_ORDER_CANCELLED = "order_cancelled";
    public static final String TARGET_ORDER_REFUNDED = "order_refunded";

    private final MetrikaOutboxRepository outboxRepository;
    public MetrikaOutboxService(MetrikaOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void recordOrderCreated(Order order) {
        enqueue(order, "order_created", TARGET_ORDER_CREATED, null);
    }

    @Transactional
    public void recordOrderPaid(Order order) {
        enqueue(order, "order_paid", TARGET_PURCHASE_PAID, order != null ? order.getTotalAmount() : null);
    }

    @Transactional
    public void recordOrderCancelled(Order order) {
        enqueue(order, "order_cancelled", TARGET_ORDER_CANCELLED, null);
    }

    @Transactional
    public void recordOrderRefunded(Order order) {
        enqueue(order, "order_refunded", TARGET_ORDER_REFUNDED, order != null ? order.getTotalAmount() : null);
    }

    private void enqueue(Order order, String eventType, String target, Money revenue) {
        if (order == null || order.getId() == null) {
            return;
        }
        if (!hasAnyIdentifier(order)) {
            return;
        }
        String eventKey = eventType + ":" + order.getId();
        if (outboxRepository.existsByEventKey(eventKey)) {
            return;
        }

        MetrikaOutbox outbox = new MetrikaOutbox();
        outbox.setEventKey(eventKey);
        outbox.setEventType(eventType);
        outbox.setOrderId(order.getId());
        outbox.setTarget(target);
        outbox.setPayload(buildCsvPayload(order, target, revenue));
        outbox.setStatus(MetrikaOutboxStatus.PENDING);
        try {
            outboxRepository.save(outbox);
        } catch (DataIntegrityViolationException ignored) {
        }
    }

    private boolean hasAnyIdentifier(Order order) {
        return StringUtils.hasText(order.getMetrikaClientId())
                || StringUtils.hasText(order.getMetrikaUserId())
                || StringUtils.hasText(order.getYclid())
                || StringUtils.hasText(order.getAnalyticsPurchaseId());
    }

    private String buildCsvPayload(Order order, String target, Money revenue) {
        OffsetDateTime eventTime = OffsetDateTime.now(ZoneOffset.UTC);
        String purchaseId = StringUtils.hasText(order.getAnalyticsPurchaseId())
                ? order.getAnalyticsPurchaseId()
                : "order-" + order.getId();
        String price = revenue == null ? "" : moneyToDecimal(revenue).toPlainString();
        String currency = revenue == null ? "" : normalizeCurrency(revenue.getCurrency());

        return String.join("\n",
                "ClientID,UserID,yclid,PurchaseId,Target,DateTime,Price,Currency",
                String.join(",",
                        csv(order.getMetrikaClientId()),
                        csv(order.getMetrikaUserId()),
                        csv(order.getYclid()),
                        csv(purchaseId),
                        csv(target),
                        csv(String.valueOf(eventTime.toEpochSecond())),
                        csv(price),
                        csv(currency)
                )
        );
    }

    private BigDecimal moneyToDecimal(Money money) {
        return money == null ? BigDecimal.ZERO : money.toBigDecimal();
    }

    private String normalizeCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : "RUB";
    }

    private String csv(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
