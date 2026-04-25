package com.example.api.order;

import com.example.api.admincms.DirectusAdminService;
import com.example.api.notification.EmailService;
import com.example.customer.domain.Customer;
import com.example.customer.service.CustomerService;
import com.example.order.domain.Order;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.service.PaymentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    private final DirectusAdminService directusAdminService;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @Value("${payment.public.confirmation-mode:REDIRECT}")
    private String defaultPaymentConfirmationMode;

    @Autowired
    public OrderController(OrderService orderService,
                           CustomerService customerService,
                           PaymentService paymentService,
                           EmailService emailService,
                           DirectusAdminService directusAdminService) {
        this.orderService = orderService;
        this.customerService = customerService;
        this.paymentService = paymentService;
        this.emailService = emailService;
        this.directusAdminService = directusAdminService;
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
        Customer customer = resolveCheckoutCustomer(request, jwt);
        UUID customerId = customer.getId();
        boolean savePaymentMethod = jwt != null && Boolean.TRUE.equals(request.savePaymentMethod());
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
                    customerId.toString(),
                    resolvePaymentConfirmationMode(request.confirmationMode)
            );
            return ResponseEntity.ok(new OrderCheckoutResponse(existingOrder, toPaymentResponse(existingPayment)));
        }

        boolean checkoutAttemptBound = false;
        try {
            Order order = orderService.createOrderFromCart(
                    request.cartId,
                    customerId,
                    request.receiptEmail,
                    null,
                    new OrderService.ContactSpec(request.customerName, request.phone, request.homeAddress)
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
                    customerId.toString(),
                    resolvePaymentConfirmationMode(request.confirmationMode)
            );
            emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new OrderCheckoutResponse(order, toPaymentResponse(payment)));
        } catch (RuntimeException ex) {
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
        Customer customer = customerService.findOrCreateByEmail(request.receiptEmail, null, null);
        customer = customerService.applyCheckoutContact(customer, request.customerName, request.phone);
        Order order = orderService.createOrderFromCart(
                request.cartId,
                customer.getId(),
                request.receiptEmail,
                null,
                new OrderService.ContactSpec(request.customerName, request.phone, request.homeAddress)
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
        Customer customer = customerService.findOrCreateByEmail(request.receiptEmail, null, null);
        customer = customerService.applyCheckoutContact(customer, request.customerName, request.phone);
        Order order = orderService.createOrderFromCart(
                request.cartId,
                customer.getId(),
                request.receiptEmail,
                managerSubject,
                new OrderService.ContactSpec(request.customerName, request.phone, request.homeAddress)
        );
        boolean shouldSend = request.sendEmail == null || Boolean.TRUE.equals(request.sendEmail);
        if (shouldSend) {
            emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
        }
        directusAdminService.recordManagerPaymentLink(order, managerSubject, jwt.getClaimAsString("email"), shouldSend);
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
                null,
                resolvePaymentConfirmationMode(request.confirmationMode)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toPaymentResponse(payment));
    }

    @PostMapping("/public/{token}/refresh-payment")
    public ResponseEntity<Order> refreshPaymentByToken(@PathVariable String token) {
        Order order = orderService.findByPublicToken(token);
        paymentService.refreshLatestYooKassaPaymentForOrder(order.getId());
        Order updated = orderService.findByPublicToken(token);
        return ResponseEntity.ok(updated);
    }

    private Customer resolveCheckoutCustomer(CheckoutRequest request, Jwt jwt) {
        Customer customer = jwt != null
                ? resolveCustomer(jwt)
                : customerService.findOrCreateByEmail(request.receiptEmail, null, null);
        return customerService.applyCheckoutContact(customer, request.customerName, request.phone);
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

    public record CheckoutRequest(
            @NotNull UUID cartId,
            @Email @NotBlank String receiptEmail,
            @NotBlank String customerName,
            @NotBlank String phone,
            @NotBlank String homeAddress,
            String returnUrl,
            String orderPageUrl,
            String confirmationMode,
            Boolean savePaymentMethod
    ) {}

    public record AdminLinkRequest(
            @NotNull UUID cartId,
            @Email @NotBlank String receiptEmail,
            @NotBlank String customerName,
            @NotBlank String phone,
            @NotBlank String homeAddress,
            String orderPageUrl
    ) {}

    public record ManagerLinkRequest(
            @NotNull UUID cartId,
            @Email @NotBlank String receiptEmail,
            @NotBlank String customerName,
            @NotBlank String phone,
            @NotBlank String homeAddress,
            String orderPageUrl,
            Boolean sendEmail
    ) {}

    public record PublicPayRequest(
            @Email @NotBlank String receiptEmail,
            String returnUrl,
            String confirmationMode
    ) {}

    public record OrderLinkResponse(UUID orderId, String publicToken) {}

    public record PaymentResponse(
            UUID paymentId,
            String confirmationUrl,
            String confirmationType,
            String confirmationToken
    ) {}

    public record OrderCheckoutResponse(Order order, PaymentResponse payment) {}

    private String buildCheckoutRequestHash(CheckoutRequest request, UUID customerId) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(customerId != null ? customerId.toString() : "guest");
        joiner.add(request.cartId != null ? request.cartId.toString() : "");
        joiner.add(normalizeValue(request.receiptEmail));
        joiner.add(normalizeValue(request.returnUrl));
        joiner.add(normalizeValue(request.orderPageUrl));
        joiner.add(normalizeValue(resolvePaymentConfirmationMode(request.confirmationMode)));
        joiner.add(Boolean.TRUE.equals(request.savePaymentMethod) ? "1" : "0");
        joiner.add(normalizeValue(request.customerName));
        joiner.add(normalizeValue(request.phone));
        joiner.add(normalizeValue(request.homeAddress));

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

    private String resolvePaymentConfirmationMode(String requestedMode) {
        String candidate = StringUtils.hasText(requestedMode)
                ? requestedMode.trim()
                : defaultPaymentConfirmationMode;
        return "EMBEDDED".equalsIgnoreCase(candidate) ? "EMBEDDED" : "REDIRECT";
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        if (payment == null) {
            return null;
        }
        return new PaymentResponse(
                payment.getId(),
                payment.getConfirmationUrl(),
                payment.getConfirmationType(),
                payment.getConfirmationToken()
        );
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
