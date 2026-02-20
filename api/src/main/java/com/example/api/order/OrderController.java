package com.example.api.order;

import com.example.api.delivery.YandexDeliveryService;
import com.example.order.domain.Order;
import com.example.customer.domain.Customer;
import com.example.customer.service.CustomerService;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.service.PaymentService;
import com.example.api.notification.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final YandexDeliveryService deliveryService;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @Autowired
    public OrderController(OrderService orderService,
                           CustomerService customerService,
                           PaymentService paymentService,
                           EmailService emailService,
                           YandexDeliveryService deliveryService) {
        this.orderService = orderService;
        this.customerService = customerService;
        this.paymentService = paymentService;
        this.emailService = emailService;
        this.deliveryService = deliveryService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestParam UUID cartId) {
        Order order = orderService.createOrderFromCart(cartId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderCheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request,
                                                          @AuthenticationPrincipal Jwt jwt,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID customerId = null;
        boolean savePaymentMethod = Boolean.TRUE.equals(request.savePaymentMethod());
        if (jwt != null) {
            Customer customer = resolveCustomer(jwt);
            customerId = customer.getId();
        } else {
            Customer customer = customerService.findOrCreateByEmail(request.receiptEmail, null, null);
            customerId = customer.getId();
            savePaymentMethod = false;
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        String requestHash = buildCheckoutRequestHash(request, customerId);
        OrderService.CheckoutAttemptState attemptState = orderService.acquireCheckoutAttempt(idempotencyKey, requestHash);
        if (attemptState.status() == OrderService.CheckoutAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Checkout request is already being processed");
        }
        if (attemptState.status() == OrderService.CheckoutAttemptStatus.COMPLETED) {
            Order existingOrder = orderService.findOrderByCheckoutAttempt(idempotencyKey, requestHash);
            String replayReturnUrl = resolveReturnUrl(request.returnUrl, request.orderPageUrl, existingOrder);
            if (!StringUtils.hasText(replayReturnUrl)) {
                throw new IllegalArgumentException("Return URL is required");
            }
            Payment existingPayment = paymentService.createYooKassaPayment(
                    existingOrder.getId(),
                    request.receiptEmail,
                    replayReturnUrl,
                    "order-" + existingOrder.getId(),
                    savePaymentMethod,
                    customerId != null ? customerId.toString() : null
            );
            return ResponseEntity.ok(new OrderCheckoutResponse(existingOrder, existingPayment));
        }

        OrderService.DeliverySpec deliverySpec = null;
        YandexDeliveryService.DeliveryConfirmResult confirmResult = null;
        boolean checkoutAttemptBound = false;
        try {
            if (request.delivery != null) {
                DeliveryRequest delivery = request.delivery;
                validateDeliveryRequest(delivery);
                YandexDeliveryService.DeliveryOffersRequest offersRequest = new YandexDeliveryService.DeliveryOffersRequest();
                offersRequest.cartId = request.cartId;
                offersRequest.deliveryType = delivery.deliveryType;
                offersRequest.address = delivery.address;
                offersRequest.pickupPointId = delivery.pickupPointId;
                offersRequest.pickupPointName = delivery.pickupPointName;
                offersRequest.intervalFrom = delivery.intervalFrom;
                offersRequest.intervalTo = delivery.intervalTo;
                offersRequest.firstName = delivery.firstName;
                offersRequest.lastName = delivery.lastName;
                offersRequest.phone = delivery.phone;
                offersRequest.email = StringUtils.hasText(delivery.email) ? delivery.email : request.receiptEmail;
                offersRequest.comment = delivery.comment;
                YandexDeliveryService.DeliveryOffer offer = deliveryService.resolveOffer(offersRequest, delivery.offerId);
                var deliveryAmount = offer.pricingTotal() != null ? offer.pricingTotal() : offer.pricing();
                if (deliveryAmount == null) {
                    throw new IllegalArgumentException("Delivery price is required");
                }
                confirmResult = deliveryService.confirmOffer(offer.offerId());
                deliverySpec = new OrderService.DeliverySpec(
                        deliveryAmount,
                        "YANDEX",
                        delivery.deliveryType.name(),
                        delivery.address,
                        delivery.pickupPointId,
                        delivery.pickupPointName,
                        offer.intervalFrom(),
                        offer.intervalTo(),
                        offer.offerId(),
                        confirmResult.requestId(),
                        "REQUESTED"
                );
            }

            Order order = orderService.createOrderFromCart(
                    request.cartId,
                    customerId,
                    request.receiptEmail,
                    null,
                    deliverySpec
            );
            orderService.completeCheckoutAttempt(idempotencyKey, requestHash, order.getId());
            checkoutAttemptBound = true;

            String returnUrl = resolveReturnUrl(request.returnUrl, request.orderPageUrl, order);
            if (!StringUtils.hasText(returnUrl)) {
                throw new IllegalArgumentException("Return URL is required");
            }
            Payment payment = paymentService.createYooKassaPayment(
                    order.getId(),
                    request.receiptEmail,
                    returnUrl,
                    "order-" + order.getId(),
                    savePaymentMethod,
                    customerId != null ? customerId.toString() : null
            );
            emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new OrderCheckoutResponse(order, payment));
        } catch (RuntimeException ex) {
            if (confirmResult != null && !checkoutAttemptBound) {
                try {
                    deliveryService.cancelRequest(confirmResult.requestId());
                } catch (Exception cancelEx) {
                }
            }
            if (!checkoutAttemptBound) {
                orderService.releaseCheckoutAttemptIfUnbound(idempotencyKey, requestHash);
            }
            throw ex;
        }
    }

    @PostMapping("/admin-link")
    public ResponseEntity<OrderLinkResponse> createAdminLink(@Valid @RequestBody AdminLinkRequest request,
                                                             @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID customerId = customerService.findOrCreateByEmail(request.receiptEmail, null, null)
                .getId();
        Order order = orderService.createOrderFromCart(
                request.cartId,
                customerId,
                request.receiptEmail
        );
        emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OrderLinkResponse(order.getId(), order.getPublicToken()));
    }

    @PostMapping("/manager-link")
    public ResponseEntity<OrderLinkResponse> createManagerLink(@Valid @RequestBody ManagerLinkRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String managerSubject = resolveManagerSubject(jwt);
        if (request.receiptEmail == null || request.receiptEmail.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UUID customerId = customerService.findOrCreateByEmail(request.receiptEmail, null, null)
                .getId();
        Order order = orderService.createOrderFromCart(
                request.cartId,
                customerId,
                request.receiptEmail,
                managerSubject
        );
        boolean shouldSend = request.sendEmail == null || Boolean.TRUE.equals(request.sendEmail);
        if (shouldSend) {
            emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OrderLinkResponse(order.getId(), order.getPublicToken()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateOrderStatus(@PathVariable UUID id,
                                                  @RequestParam String status) {
        Order beforeUpdate = orderService.findById(id);
        String previousStatus = beforeUpdate.getStatus();
        Order updated = orderService.updateOrderStatus(id, status);
        if (hasStatusChanged(previousStatus, updated.getStatus()) && StringUtils.hasText(updated.getReceiptEmail())) {
            emailService.sendOrderStatusUpdatedEmail(updated, updated.getReceiptEmail(), previousStatus);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/delivery/refresh")
    public ResponseEntity<Order> refreshDelivery(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        if (order.getDeliveryRequestId() == null || order.getDeliveryRequestId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        YandexDeliveryService.DeliveryRequestInfo info = deliveryService.getRequestInfo(order.getDeliveryRequestId());
        Order updated = orderService.updateDeliveryStatus(
                order.getId(),
                info.status(),
                info.intervalFrom(),
                info.intervalTo()
        );
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/delivery/cancel")
    public ResponseEntity<Order> cancelDelivery(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        if (order.getDeliveryRequestId() == null || order.getDeliveryRequestId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        deliveryService.cancelRequest(order.getDeliveryRequestId());
        Order updated = orderService.updateDeliveryStatus(order.getId(), "CANCELLED", null, null);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/me")
    public ResponseEntity<List<Order>> getMyOrders(@AuthenticationPrincipal Jwt jwt) {
        Customer customer = resolveCustomer(jwt);
        return ResponseEntity.ok(orderService.getOrdersByCustomerId(customer.getId()));
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/public/{token}")
    public ResponseEntity<Order> getOrderByToken(@PathVariable String token) {
        Order order = orderService.findByPublicToken(token);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/public/{token}/pay")
    public ResponseEntity<PaymentResponse> payByToken(@PathVariable String token,
                                                      @Valid @RequestBody PublicPayRequest request) {
        Order order = orderService.findByPublicToken(token);
        String returnUrl = resolveReturnUrl(request.returnUrl, null, order);
        if (returnUrl == null || returnUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Payment payment = paymentService.createYooKassaPayment(
                order.getId(),
                request.receiptEmail,
                returnUrl,
                "order-" + order.getId(),
                false,
                null
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PaymentResponse(payment.getId(), payment.getConfirmationUrl()));
    }

    @PostMapping("/public/{token}/refresh-payment")
    public ResponseEntity<Order> refreshPaymentByToken(@PathVariable String token) {
        Order order = orderService.findByPublicToken(token);
        paymentService.refreshLatestYooKassaPaymentForOrder(order.getId());
        Order updated = orderService.findByPublicToken(token);
        return ResponseEntity.ok(updated);
    }

    private Customer resolveCustomer(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Missing authentication token");
        }
        String email = jwt.getClaimAsString("email");
        String subject = jwt.getSubject();
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        String displayName = jwt.getClaimAsString("name");
        if ((!StringUtils.hasText(firstName) || !StringUtils.hasText(lastName)) && StringUtils.hasText(displayName)) {
            String[] parts = displayName.trim().split("\\s+");
            if (!StringUtils.hasText(firstName) && parts.length > 0) {
                firstName = parts[0];
            }
            if (!StringUtils.hasText(lastName) && parts.length > 1) {
                lastName = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }
        }
        if (!StringUtils.hasText(email) && StringUtils.hasText(preferredUsername) && preferredUsername.contains("@")) {
            email = preferredUsername;
        }
        if (!StringUtils.hasText(email) && StringUtils.hasText(subject) && subject.contains("@")) {
            email = subject;
        }
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email is required to resolve customer profile");
        }
        return customerService.findOrCreateByEmail(email, firstName, lastName);
    }

    private String resolveManagerSubject(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Missing authentication token");
        }
        String subject = jwt.getSubject();
        if (StringUtils.hasText(subject)) {
            return subject;
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        String email = jwt.getClaimAsString("email");
        if (StringUtils.hasText(email)) {
            return email;
        }
        throw new IllegalArgumentException("Manager subject is required");
    }

    private void validateDeliveryRequest(DeliveryRequest delivery) {
        if (delivery == null) {
            return;
        }
        if (delivery.deliveryType == null) {
            throw new IllegalArgumentException("Delivery type is required");
        }
        if (!StringUtils.hasText(delivery.offerId)) {
            throw new IllegalArgumentException("Delivery offer is required");
        }
        if (!StringUtils.hasText(delivery.firstName)) {
            throw new IllegalArgumentException("Recipient first name is required");
        }
        if (!StringUtils.hasText(delivery.phone)) {
            throw new IllegalArgumentException("Recipient phone is required");
        }
        if (delivery.deliveryType == YandexDeliveryService.DeliveryType.COURIER) {
            if (!StringUtils.hasText(delivery.address)) {
                throw new IllegalArgumentException("Delivery address is required");
            }
        } else if (delivery.deliveryType == YandexDeliveryService.DeliveryType.PICKUP) {
            if (!StringUtils.hasText(delivery.pickupPointId)) {
                throw new IllegalArgumentException("Pickup point is required");
            }
        }
    }

    public record DeliveryRequest(
            YandexDeliveryService.DeliveryType deliveryType,
            String offerId,
            String address,
            String pickupPointId,
            String pickupPointName,
            OffsetDateTime intervalFrom,
            OffsetDateTime intervalTo,
            String firstName,
            String lastName,
            String phone,
            String email,
            String comment
    ) {}

    public record CheckoutRequest(
            @NotNull UUID cartId,
            @Email @NotBlank String receiptEmail,
            String returnUrl,
            String orderPageUrl,
            Boolean savePaymentMethod,
            DeliveryRequest delivery
    ) {}

    public record AdminLinkRequest(
            @NotNull UUID cartId,
            @Email @NotBlank String receiptEmail,
            String orderPageUrl
    ) {}

    public record ManagerLinkRequest(
            @NotNull UUID cartId,
            @Email String receiptEmail,
            String orderPageUrl,
            Boolean sendEmail
    ) {}

    public record PublicPayRequest(
            @Email @NotBlank String receiptEmail,
            String returnUrl
    ) {}

    public record OrderLinkResponse(UUID orderId, String publicToken) {}

    public record PaymentResponse(UUID paymentId, String confirmationUrl) {}

    public record OrderCheckoutResponse(Order order, Payment payment) {}

    private String buildCheckoutRequestHash(CheckoutRequest request, UUID customerId) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(customerId != null ? customerId.toString() : "guest");
        joiner.add(request.cartId != null ? request.cartId.toString() : "");
        joiner.add(normalizeValue(request.receiptEmail));
        joiner.add(normalizeValue(request.returnUrl));
        joiner.add(normalizeValue(request.orderPageUrl));
        joiner.add(Boolean.TRUE.equals(request.savePaymentMethod) ? "1" : "0");

        DeliveryRequest delivery = request.delivery;
        if (delivery != null) {
            joiner.add(delivery.deliveryType != null ? delivery.deliveryType.name() : "");
            joiner.add(normalizeValue(delivery.offerId));
            joiner.add(normalizeValue(delivery.address));
            joiner.add(normalizeValue(delivery.pickupPointId));
            joiner.add(normalizeValue(delivery.pickupPointName));
            joiner.add(delivery.intervalFrom != null ? delivery.intervalFrom.toString() : "");
            joiner.add(delivery.intervalTo != null ? delivery.intervalTo.toString() : "");
            joiner.add(normalizeValue(delivery.firstName));
            joiner.add(normalizeValue(delivery.lastName));
            joiner.add(normalizeValue(delivery.phone));
            joiner.add(normalizeValue(delivery.email));
            joiner.add(normalizeValue(delivery.comment));
        }

        return sha256(joiner.toString());
    }

    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash checkout request");
        }
    }

    private String buildOrderUrl(String overrideUrl, Order order) {
        if (overrideUrl != null && !overrideUrl.isBlank()) {
            return applyTokenTemplate(overrideUrl, order);
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        return base + "/order/" + order.getPublicToken();
    }

    private String resolveReturnUrl(String returnUrl, String overrideOrderUrl, Order order) {
        if (returnUrl != null && !returnUrl.isBlank()) {
            return applyTokenTemplate(returnUrl, order);
        }
        return buildOrderUrl(overrideOrderUrl, order);
    }

    private String applyTokenTemplate(String value, Order order) {
        if (value == null) {
            return null;
        }
        String token = order.getPublicToken();
        if (token == null) {
            return value;
        }
        return value.replace("{token}", token);
    }

    private boolean hasStatusChanged(String previousStatus, String nextStatus) {
        if (previousStatus == null && nextStatus == null) {
            return false;
        }
        if (previousStatus == null || nextStatus == null) {
            return true;
        }
        return !previousStatus.equalsIgnoreCase(nextStatus);
    }
}
