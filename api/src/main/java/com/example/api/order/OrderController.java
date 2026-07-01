package com.example.api.order;

import com.example.api.admincms.DirectusAdminService;
import com.example.api.auth.MagicLinkService;
import com.example.api.metrika.MetrikaOutboxService;
import com.example.api.notification.EmailService;
import com.example.api.notification.NotificationOrchestrator;
import com.example.customer.domain.Customer;
import com.example.customer.service.CustomerService;
import com.example.order.domain.Order;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final Pattern EMAIL_LIKE_VALUE = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_LIKE_VALUE = Pattern.compile("(?:\\+?\\d[\\s().-]*){8,}");

    private final OrderService orderService;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final DirectusAdminService directusAdminService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final MagicLinkService magicLinkService;
    private final MetrikaOutboxService metrikaOutboxService;
    private final ObjectMapper objectMapper;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @Value("${payment.public.confirmation-mode:REDIRECT}")
    private String defaultPaymentConfirmationMode;

    public OrderController(OrderService orderService,
                           CustomerService customerService,
                           PaymentService paymentService,
                           EmailService emailService,
                           DirectusAdminService directusAdminService,
                           NotificationOrchestrator notificationOrchestrator) {
        this(
                orderService,
                customerService,
                paymentService,
                emailService,
                directusAdminService,
                notificationOrchestrator,
                null,
                null,
                new ObjectMapper()
        );
    }

    @Autowired
    public OrderController(OrderService orderService,
                           CustomerService customerService,
                           PaymentService paymentService,
                           EmailService emailService,
                           DirectusAdminService directusAdminService,
                           NotificationOrchestrator notificationOrchestrator,
                           MagicLinkService magicLinkService,
                           MetrikaOutboxService metrikaOutboxService,
                           ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.customerService = customerService;
        this.paymentService = paymentService;
        this.emailService = emailService;
        this.directusAdminService = directusAdminService;
        this.notificationOrchestrator = notificationOrchestrator;
        this.magicLinkService = magicLinkService;
        this.metrikaOutboxService = metrikaOutboxService;
        this.objectMapper = objectMapper;
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
        String effectiveIdempotencyKey = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey
                : request.idempotencyKey();
        if (!StringUtils.hasText(effectiveIdempotencyKey)) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        String requestHash = buildCheckoutRequestHash(request, customerId);
        OrderService.CheckoutAttemptState attemptState = orderService.acquireCheckoutAttempt(effectiveIdempotencyKey, requestHash);
        if (attemptState.status() == OrderService.CheckoutAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Checkout request is already being processed");
        }
        if (attemptState.status() == OrderService.CheckoutAttemptStatus.COMPLETED) {
            Order existingOrder = orderService.findOrderByCheckoutAttempt(effectiveIdempotencyKey, requestHash);
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
            notifyPaymentCompletedIfNeeded(existingOrder.getStatus(), existingPayment);
            return ResponseEntity.ok(new OrderCheckoutResponse(
                    attachPaymentSummary(existingOrder),
                    toPaymentResponse(existingPayment),
                    buildCheckoutAccountResponse(request, jwt, customer, existingOrder)
            ));
        }

        boolean checkoutAttemptBound = false;
        try {
            Order order = orderService.createOrderFromCartAndCompleteCheckoutAttempt(
                    effectiveIdempotencyKey,
                    requestHash,
                    request.cartId,
                    customerId,
                    request.receiptEmail,
                    null,
                    new OrderService.ContactSpec(request.customerName, request.phone, request.homeAddress),
                    toOrderAnalyticsAttribution(request.analyticsAttribution)
            );
            checkoutAttemptBound = true;
            recordOrderCreated(order);

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
            notifyPaymentCompletedIfNeeded("PENDING", payment);
            emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new OrderCheckoutResponse(
                            attachPaymentSummary(order),
                            toPaymentResponse(payment),
                            buildCheckoutAccountResponse(request, jwt, customer, order)
                    ));
        } catch (RuntimeException ex) {
            if (!checkoutAttemptBound) {
                orderService.releaseCheckoutAttemptIfUnbound(effectiveIdempotencyKey, requestHash);
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
        recordOrderCreated(order);
        emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toOrderLinkResponse(order, request.orderPageUrl, true));
    }

    @PostMapping("/manager-link")
    public ResponseEntity<OrderLinkResponse> createManagerLink(@Valid @RequestBody ManagerLinkRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String managerSubject = resolveManagerSubject(jwt);
        String effectiveIdempotencyKey = normalizeValue(request.idempotencyKey);
        if (!StringUtils.hasText(effectiveIdempotencyKey)) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        String requestHash = buildManagerLinkRequestHash(request, managerSubject);
        boolean shouldSend = request.sendEmail == null || Boolean.TRUE.equals(request.sendEmail);
        OrderService.CheckoutAttemptState attemptState = orderService.acquireCheckoutAttempt(effectiveIdempotencyKey, requestHash);
        if (attemptState.status() == OrderService.CheckoutAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Manager link request is already being processed");
        }
        if (attemptState.status() == OrderService.CheckoutAttemptStatus.COMPLETED) {
            Order existingOrder = orderService.findOrderByCheckoutAttempt(effectiveIdempotencyKey, requestHash);
            boolean alreadySent = directusAdminService.isManagerPaymentLinkSent(existingOrder.getId());
            boolean emailSent = alreadySent;
            if (shouldSend && !alreadySent) {
                emailService.sendOrderCreatedEmail(existingOrder, request.receiptEmail, buildOrderUrl(request.orderPageUrl, existingOrder));
                emailSent = true;
            }
            directusAdminService.recordManagerPaymentLink(existingOrder, managerSubject, jwt.getClaimAsString("email"), emailSent);
            return ResponseEntity.ok(toOrderLinkResponse(existingOrder, request.orderPageUrl, emailSent));
        }

        boolean orderBound = false;
        Customer customer = customerService.findOrCreateByEmail(request.receiptEmail, null, null);
        customer = customerService.applyCheckoutContact(customer, request.customerName, request.phone);
        try {
            Order order = orderService.createOrderFromCartAndCompleteCheckoutAttempt(
                    effectiveIdempotencyKey,
                    requestHash,
                    request.cartId,
                    customer.getId(),
                    request.receiptEmail,
                    managerSubject,
                    new OrderService.ContactSpec(request.customerName, request.phone, request.homeAddress)
            );
            orderBound = true;
            recordOrderCreated(order);
            directusAdminService.recordManagerPaymentLink(order, managerSubject, jwt.getClaimAsString("email"), false);
            boolean emailSent = false;
            if (shouldSend) {
                emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
                emailSent = true;
                directusAdminService.recordManagerPaymentLink(order, managerSubject, jwt.getClaimAsString("email"), true);
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toOrderLinkResponse(order, request.orderPageUrl, emailSent));
        } catch (RuntimeException ex) {
            if (!orderBound) {
                orderService.releaseCheckoutAttemptIfUnbound(effectiveIdempotencyKey, requestHash);
            }
            throw ex;
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateOrderStatus(@PathVariable UUID id,
                                                  @RequestParam String status) {
        Order beforeUpdate = orderService.findById(id);
        String previousStatus = beforeUpdate.getStatus();
        Order updated = orderService.updateOrderStatus(id, status);
        notificationOrchestrator.orderStatusChanged(updated, previousStatus);
        recordStatusConversion(updated);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<List<Order>> getMyOrders(@AuthenticationPrincipal Jwt jwt) {
        Customer customer = resolveCustomer(jwt);
        return ResponseEntity.ok(attachPaymentSummaries(orderService.getOrdersByCustomerId(customer.getId())));
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(attachPaymentSummaries(orderService.getAllOrders()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        return ResponseEntity.ok(attachPaymentSummary(order));
    }

    @GetMapping("/public/{token}")
    public ResponseEntity<Order> getOrderByToken(@PathVariable String token) {
        Order order = orderService.findByPublicToken(token);
        return ResponseEntity.ok(attachPaymentSummary(order));
    }

    @PostMapping("/public/{token}/pay")
    public ResponseEntity<PaymentResponse> payByToken(@PathVariable String token,
                                                      @Valid @RequestBody PublicPayRequest request) {
        Order order = orderService.findByPublicToken(token);
        OrderService.AnalyticsAttribution attribution = toOrderAnalyticsAttribution(request.analyticsAttribution);
        if (attribution != null) {
            order = orderService.updateAnalyticsAttribution(order.getId(), attribution);
        }
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
        notifyPaymentCompletedIfNeeded(order.getStatus(), payment);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toPaymentResponse(payment));
    }

    @PostMapping("/public/{token}/refresh-payment")
    public ResponseEntity<Order> refreshPaymentByToken(@PathVariable String token) {
        Order order = orderService.findByPublicToken(token);
        PaymentService.PaymentUpdateResult result = paymentService.refreshLatestYooKassaPaymentForOrder(order.getId());
        Order updated = orderService.findByPublicToken(token);
        if (result.completedNow()) {
            notificationOrchestrator.orderPaid(updated, result.payment());
            directusAdminService.markManagerPaymentLinkPaid(updated.getId());
            recordOrderPaid(updated);
        }
        return ResponseEntity.ok(attachPaymentSummary(updated));
    }

    private Customer resolveCheckoutCustomer(CheckoutRequest request, Jwt jwt) {
        Customer customer = jwt != null
                ? resolveCustomer(jwt)
                : customerService.findOrCreateByEmail(request.receiptEmail, null, null);
        return customerService.applyCheckoutContact(
                customer,
                request.customerName,
                request.phone,
                toCheckoutAddressSpec(request.addressParts)
        );
    }

    private CheckoutAccountResponse buildCheckoutAccountResponse(CheckoutRequest request,
                                                                 Jwt jwt,
                                                                 Customer customer,
                                                                 Order order) {
        String redirectPath = buildAccountRedirectPath(order);
        String email = normalizeEmail(customer != null ? customer.getEmail() : request.receiptEmail);
        if (jwt != null) {
            return new CheckoutAccountResponse(CheckoutAccountStatus.AUTHENTICATED, redirectPath, email);
        }
        String accountRedirectUrl = resolveAccountRedirectUrl(request.accountRedirectUrl, order);
        if (magicLinkService == null || !StringUtils.hasText(accountRedirectUrl)) {
            return new CheckoutAccountResponse(CheckoutAccountStatus.UNAVAILABLE, redirectPath, email);
        }
        MagicLinkService.MagicLinkResult result;
        try {
            result = magicLinkService.requestMagicLink(email, accountRedirectUrl);
        } catch (RuntimeException ex) {
            return new CheckoutAccountResponse(CheckoutAccountStatus.UNAVAILABLE, redirectPath, email);
        }
        CheckoutAccountStatus status = switch (result.status()) {
            case ACCEPTED -> CheckoutAccountStatus.MAGIC_LINK_SENT;
            case RATE_LIMITED -> CheckoutAccountStatus.MAGIC_LINK_RATE_LIMITED;
            case VALIDATION_ERROR, UNAVAILABLE -> CheckoutAccountStatus.UNAVAILABLE;
        };
        return new CheckoutAccountResponse(status, redirectPath, email);
    }

    private String buildAccountRedirectPath(Order order) {
        if (order == null || order.getId() == null) {
            return "/account#orders";
        }
        return "/account?order=" + order.getId() + "#orders";
    }

    private String resolveAccountRedirectUrl(String redirectUrl, Order order) {
        String normalized = normalizeValue(redirectUrl);
        if (!StringUtils.hasText(normalized) || order == null) {
            return normalized;
        }
        String orderId = order.getId() == null ? "" : order.getId().toString();
        String publicToken = normalizeValue(order.getPublicToken());
        return normalized
                .replace("{orderId}", orderId)
                .replace("{token}", publicToken);
    }

    private CustomerService.CheckoutAddressSpec toCheckoutAddressSpec(AddressPartsRequest request) {
        if (request == null) {
            return null;
        }
        return new CustomerService.CheckoutAddressSpec(
                request.postalCode(),
                request.city(),
                request.street(),
                request.address2()
        );
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

    private OrderService.AnalyticsAttribution toOrderAnalyticsAttribution(AnalyticsAttributionRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, String> utm = request.utm == null ? Map.of() : request.utm;
        return new OrderService.AnalyticsAttribution(
                safeAnalyticsValue(request.metrikaClientId),
                safeAnalyticsValue(request.metrikaUserId),
                safeAnalyticsValue(request.yclid),
                safeAnalyticsValue(utm.get("utm_source")),
                safeAnalyticsValue(utm.get("utm_medium")),
                safeAnalyticsValue(utm.get("utm_campaign")),
                safeAnalyticsValue(utm.get("utm_content")),
                safeAnalyticsValue(utm.get("utm_term")),
                safeAnalyticsValue(utm.get("utm_id")),
                safeAnalyticsValue(request.landingPage),
                safeAnalyticsValue(request.currentPath),
                safeAnalyticsValue(request.referrer),
                request.sessionStartedAt,
                safeAnalyticsValue(request.purchaseId),
                serializeAnalyticsAttribution(request)
        );
    }

    private String safeAnalyticsValue(String value) {
        String normalized = normalizeValue(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (EMAIL_LIKE_VALUE.matcher(normalized).find() || PHONE_LIKE_VALUE.matcher(normalized).matches()) {
            return "";
        }
        return normalized;
    }

    private String serializeAnalyticsAttribution(AnalyticsAttributionRequest request) {
        Map<String, String> utm = request.utm == null ? Map.of() : request.utm;
        Map<String, Object> safePayload = new LinkedHashMap<>();
        safePayload.put("metrikaClientId", safeAnalyticsValue(request.metrikaClientId));
        safePayload.put("metrikaUserId", safeAnalyticsValue(request.metrikaUserId));
        safePayload.put("yclid", safeAnalyticsValue(request.yclid));
        safePayload.put("utm", Map.of(
                "utm_source", safeAnalyticsValue(utm.get("utm_source")),
                "utm_medium", safeAnalyticsValue(utm.get("utm_medium")),
                "utm_campaign", safeAnalyticsValue(utm.get("utm_campaign")),
                "utm_content", safeAnalyticsValue(utm.get("utm_content")),
                "utm_term", safeAnalyticsValue(utm.get("utm_term")),
                "utm_id", safeAnalyticsValue(utm.get("utm_id"))
        ));
        safePayload.put("landingPage", safeAnalyticsValue(request.landingPage));
        safePayload.put("currentPath", safeAnalyticsValue(request.currentPath));
        safePayload.put("referrer", safeAnalyticsValue(request.referrer));
        safePayload.put("sessionStartedAt", request.sessionStartedAt);
        safePayload.put("purchaseId", safeAnalyticsValue(request.purchaseId));
        safePayload.put("deviceType", safeAnalyticsValue(request.deviceType));
        safePayload.put("browserFamily", safeAnalyticsValue(request.browserFamily));
        safePayload.put("firstVisit", request.firstVisit);
        try {
            return objectMapper.writeValueAsString(safePayload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private void recordOrderCreated(Order order) {
        if (metrikaOutboxService != null) {
            metrikaOutboxService.recordOrderCreated(order);
        }
    }

    private void recordOrderPaid(Order order) {
        if (metrikaOutboxService != null) {
            metrikaOutboxService.recordOrderPaid(order);
        }
    }

    private void recordStatusConversion(Order order) {
        if (metrikaOutboxService == null || order == null || !StringUtils.hasText(order.getStatus())) {
            return;
        }
        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            metrikaOutboxService.recordOrderCancelled(order);
        } else if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            metrikaOutboxService.recordOrderRefunded(order);
        }
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
            Boolean savePaymentMethod,
            AnalyticsAttributionRequest analyticsAttribution,
            String accountRedirectUrl,
            AddressPartsRequest addressParts,
            String idempotencyKey
    ) {}

    public record AddressPartsRequest(
            String postalCode,
            String city,
            String street,
            String address2
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
            Boolean sendEmail,
            @NotBlank String idempotencyKey
    ) {}

    public record PublicPayRequest(
            @Email @NotBlank String receiptEmail,
            String returnUrl,
            String confirmationMode,
            AnalyticsAttributionRequest analyticsAttribution
    ) {}

    public record AnalyticsAttributionRequest(
            String metrikaClientId,
            String metrikaUserId,
            String yclid,
            Map<String, String> utm,
            String landingPage,
            String currentPath,
            String referrer,
            OffsetDateTime sessionStartedAt,
            String purchaseId,
            String deviceType,
            String browserFamily,
            Boolean firstVisit
    ) {}

    public record OrderLinkResponse(UUID orderId, String publicToken, String orderUrl, boolean emailSent) {}

    public record PaymentResponse(
            UUID paymentId,
            String confirmationUrl,
            String confirmationType,
            String confirmationToken
    ) {}

    public record CheckoutAccountResponse(CheckoutAccountStatus status, String redirectPath, String email) {}

    public enum CheckoutAccountStatus {
        AUTHENTICATED,
        MAGIC_LINK_SENT,
        MAGIC_LINK_RATE_LIMITED,
        UNAVAILABLE
    }

    public record OrderCheckoutResponse(Order order, PaymentResponse payment, CheckoutAccountResponse account) {}

    private String buildCheckoutRequestHash(CheckoutRequest request, UUID customerId) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(customerId != null ? customerId.toString() : "guest");
        joiner.add(request.cartId != null ? request.cartId.toString() : "");
        joiner.add(normalizeValue(request.receiptEmail));
        joiner.add(normalizeValue(request.returnUrl));
        joiner.add(normalizeValue(request.orderPageUrl));
        joiner.add(normalizeValue(request.accountRedirectUrl));
        joiner.add(normalizeValue(resolvePaymentConfirmationMode(request.confirmationMode)));
        joiner.add(Boolean.TRUE.equals(request.savePaymentMethod) ? "1" : "0");
        joiner.add(normalizeValue(request.customerName));
        joiner.add(normalizeValue(request.phone));
        joiner.add(normalizeValue(request.homeAddress));
        AddressPartsRequest addressParts = request.addressParts;
        joiner.add(addressParts == null ? "" : normalizeValue(addressParts.postalCode()));
        joiner.add(addressParts == null ? "" : normalizeValue(addressParts.city()));
        joiner.add(addressParts == null ? "" : normalizeValue(addressParts.street()));
        joiner.add(addressParts == null ? "" : normalizeValue(addressParts.address2()));

        return sha256(joiner.toString());
    }

    private String buildManagerLinkRequestHash(ManagerLinkRequest request, String managerSubject) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add("manager-link");
        joiner.add(normalizeValue(managerSubject));
        joiner.add(request.cartId != null ? request.cartId.toString() : "");
        joiner.add(normalizeValue(request.receiptEmail));
        joiner.add(normalizeValue(request.orderPageUrl));
        joiner.add(request.sendEmail == null || Boolean.TRUE.equals(request.sendEmail) ? "1" : "0");
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

    private String normalizeEmail(String value) {
        return normalizeValue(value).toLowerCase();
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

    private OrderLinkResponse toOrderLinkResponse(Order order, String overrideOrderUrl, boolean emailSent) {
        return new OrderLinkResponse(
                order.getId(),
                order.getPublicToken(),
                buildOrderUrl(overrideOrderUrl, order),
                emailSent
        );
    }

    private List<Order> attachPaymentSummaries(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return orders;
        }
        orders.forEach(this::attachPaymentSummary);
        return orders;
    }

    private Order attachPaymentSummary(Order order) {
        if (order != null && order.getId() != null) {
            order.setPaymentSummary(paymentService.getPaymentSummary(order.getId()));
        }
        return order;
    }

    private void notifyPaymentCompletedIfNeeded(String previousOrderStatus, Payment payment) {
        if (payment == null || payment.getStatus() == null || !"COMPLETED".equals(payment.getStatus().name())) {
            return;
        }
        if ("PAID".equalsIgnoreCase(previousOrderStatus)) {
            return;
        }
        Order updated = orderService.findById(payment.getOrderId());
        notificationOrchestrator.orderPaid(updated, payment);
        directusAdminService.markManagerPaymentLinkPaid(updated.getId());
        recordOrderPaid(updated);
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

}
