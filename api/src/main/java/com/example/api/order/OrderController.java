package com.example.api.order;

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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final EmailService emailService;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @Autowired
    public OrderController(OrderService orderService,
                           CustomerService customerService,
                           PaymentService paymentService,
                           EmailService emailService) {
        this.orderService = orderService;
        this.customerService = customerService;
        this.paymentService = paymentService;
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestParam UUID cartId) {
        Order order = orderService.createOrderFromCart(cartId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderCheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        UUID customerId = null;
        boolean savePaymentMethod = Boolean.TRUE.equals(request.savePaymentMethod());
        if (jwt != null) {
            Customer customer = resolveCustomer(jwt);
            customerId = customer.getId();
        } else {
            savePaymentMethod = false;
        }
        Order order = orderService.createOrderFromCart(
                request.cartId,
                customerId,
                request.receiptEmail
        );
        String returnUrl = resolveReturnUrl(request.returnUrl, request.orderPageUrl, order);
        if (returnUrl == null || returnUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
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
    }

    @PostMapping("/admin-link")
    public ResponseEntity<OrderLinkResponse> createAdminLink(@Valid @RequestBody AdminLinkRequest request,
                                                             @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID customerId = customerService.findByEmail(request.receiptEmail)
                .map(Customer::getId)
                .orElse(null);
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
        UUID customerId = null;
        if (request.receiptEmail != null && !request.receiptEmail.isBlank()) {
            customerId = customerService.findByEmail(request.receiptEmail)
                    .map(Customer::getId)
                    .orElse(null);
        }
        Order order = orderService.createOrderFromCart(
                request.cartId,
                customerId,
                request.receiptEmail,
                managerSubject
        );
        boolean shouldSend = request.sendEmail == null || Boolean.TRUE.equals(request.sendEmail);
        if (shouldSend) {
            if (request.receiptEmail == null || request.receiptEmail.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            emailService.sendOrderCreatedEmail(order, request.receiptEmail, buildOrderUrl(request.orderPageUrl, order));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OrderLinkResponse(order.getId(), order.getPublicToken()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateOrderStatus(@PathVariable UUID id,
                                                  @RequestParam String status) {
        orderService.updateOrderStatus(id, status);
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

    public record CheckoutRequest(
            @NotNull UUID cartId,
            @Email @NotBlank String receiptEmail,
            String returnUrl,
            String orderPageUrl,
            Boolean savePaymentMethod
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
}
