package com.example.api.order;

import com.example.customer.domain.Customer;
import com.example.customer.service.CustomerService;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.RmaRequest;
import com.example.order.domain.RmaRequestItem;
import com.example.order.domain.RmaStatus;
import com.example.order.repository.RmaRequestRepository;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/orders/me/{orderId}/rma-requests")
public class CustomerRmaController {
    private static final Set<String> RETURNABLE_STATUSES = Set.of("PAID", "DELIVERED", "RECEIVED");

    private final OrderService orderService;
    private final CustomerService customerService;
    private final RmaRequestRepository rmaRequestRepository;

    public CustomerRmaController(OrderService orderService,
                                 CustomerService customerService,
                                 RmaRequestRepository rmaRequestRepository) {
        this.orderService = orderService;
        this.customerService = customerService;
        this.rmaRequestRepository = rmaRequestRepository;
    }

    @GetMapping
    public ResponseEntity<CustomerRmaListResponse> list(@PathVariable UUID orderId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        Order order = requireCustomerOrder(orderId, jwt);
        List<CustomerRmaView> items = rmaRequestRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .map(CustomerRmaView::from)
                .toList();
        return ResponseEntity.ok(new CustomerRmaListResponse(items));
    }

    @PostMapping
    public ResponseEntity<CustomerRmaView> create(@PathVariable UUID orderId,
                                                  @Valid @RequestBody CustomerRmaCreateRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        Order order = requireCustomerOrder(orderId, jwt);
        requireReturnable(order);

        RmaRequest rma = new RmaRequest();
        rma.setRmaNumber(nextRmaNumber());
        rma.setOrderId(order.getId());
        rma.setCustomerEmail(order.getReceiptEmail());
        rma.setStatus(RmaStatus.REQUESTED);
        rma.setReason(normalize(request.reason()));
        rma.setDesiredResolution(normalize(request.desiredResolution()));

        Set<UUID> seenItems = new HashSet<>();
        for (CustomerRmaItemRequest itemRequest : request.items()) {
            OrderItem orderItem = findOrderItem(order, itemRequest.orderItemId());
            if (!seenItems.add(orderItem.getId())) {
                throw new IllegalArgumentException("Duplicate order item in RMA request");
            }
            int quantity = itemRequest.quantity();
            if (quantity > orderItem.getQuantity()) {
                throw new IllegalArgumentException("Return quantity exceeds ordered quantity");
            }
            rma.addItem(new RmaRequestItem(orderItem.getId(), quantity));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerRmaView.from(rmaRequestRepository.save(rma)));
    }

    private Order requireCustomerOrder(UUID orderId, Jwt jwt) {
        Customer customer = resolveCustomer(jwt);
        Order order = orderService.findById(orderId);
        if (order.getCustomerId() == null || !order.getCustomerId().equals(customer.getId())) {
            throw new AccessDeniedException("Order does not belong to the current customer");
        }
        return order;
    }

    private Customer resolveCustomer(Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("Missing authentication token");
        }
        String email = jwt.getClaimAsString("email");
        String subject = jwt.getSubject();
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (!StringUtils.hasText(email) && StringUtils.hasText(preferredUsername) && preferredUsername.contains("@")) {
            email = preferredUsername;
        }
        if (!StringUtils.hasText(email) && StringUtils.hasText(subject) && subject.contains("@")) {
            email = subject;
        }
        if (!StringUtils.hasText(email)) {
            throw new AccessDeniedException("Email is required to resolve customer profile");
        }
        return customerService.findOrCreateByEmail(email, jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"));
    }

    private void requireReturnable(Order order) {
        String status = order.getStatus() != null ? order.getStatus().toUpperCase(Locale.ROOT) : "";
        if (!RETURNABLE_STATUSES.contains(status)) {
            throw new IllegalStateException("Returns are available after payment is confirmed");
        }
    }

    private OrderItem findOrderItem(Order order, UUID orderItemId) {
        if (orderItemId == null) {
            throw new IllegalArgumentException("orderItemId is required");
        }
        return order.getItems().stream()
                .filter(item -> orderItemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Order item does not belong to this order"));
    }

    private String nextRmaNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "RMA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            if (!rmaRequestRepository.existsByRmaNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate unique RMA number");
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public record CustomerRmaCreateRequest(
            String reason,
            String desiredResolution,
            @NotEmpty List<@Valid CustomerRmaItemRequest> items
    ) {}

    public record CustomerRmaItemRequest(
            @NotNull UUID orderItemId,
            @Min(1) int quantity
    ) {}

    public record CustomerRmaListResponse(List<CustomerRmaView> items) {}

    public record CustomerRmaView(
            UUID id,
            String rmaNumber,
            UUID orderId,
            String customerEmail,
            String status,
            String reason,
            String desiredResolution,
            String managerComment,
            String decidedBy,
            OffsetDateTime decidedAt,
            int decisionVersion,
            List<CustomerRmaItemView> items,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        static CustomerRmaView from(RmaRequest request) {
            return new CustomerRmaView(
                    request.getId(),
                    request.getRmaNumber(),
                    request.getOrderId(),
                    request.getCustomerEmail(),
                    request.getStatus() != null ? request.getStatus().name() : null,
                    request.getReason(),
                    request.getDesiredResolution(),
                    request.getManagerComment(),
                    request.getDecidedBy(),
                    request.getDecidedAt(),
                    request.getDecisionVersion(),
                    request.getItems() != null
                            ? request.getItems().stream().map(CustomerRmaItemView::from).toList()
                            : List.of(),
                    request.getCreatedAt(),
                    request.getUpdatedAt()
            );
        }
    }

    public record CustomerRmaItemView(
            UUID id,
            UUID orderItemId,
            int quantity,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        static CustomerRmaItemView from(RmaRequestItem item) {
            return new CustomerRmaItemView(
                    item.getId(),
                    item.getOrderItemId(),
                    item.getQuantity(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            );
        }
    }
}
