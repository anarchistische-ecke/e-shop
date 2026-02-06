package com.example.api.manager;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/managers")
public class ManagerController {

    private static final int DEFAULT_RECENT_LIMIT = 8;
    private static final int MAX_RECENT_LIMIT = 20;

    private final OrderRepository orderRepository;

    public ManagerController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<ManagerProfile> getProfile(@AuthenticationPrincipal Jwt jwt) {
        ManagerIdentity identity = resolveManager(jwt);
        return ResponseEntity.ok(toProfile(identity));
    }

    @GetMapping("/me/dashboard")
    public ResponseEntity<ManagerDashboard> getDashboard(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam(name = "limit", defaultValue = "8") int limit) {
        ManagerIdentity identity = resolveManager(jwt);
        String managerSubject = identity.subject();
        int effectiveLimit = limit > 0 ? limit : DEFAULT_RECENT_LIMIT;
        int safeLimit = Math.max(1, Math.min(effectiveLimit, MAX_RECENT_LIMIT));

        long totalOrders = orderRepository.countByManagerSubject(managerSubject);
        long paidOrders = orderRepository.countByManagerSubjectAndStatus(managerSubject, "PAID");
        long pendingOrders = orderRepository.countByManagerSubjectAndStatus(managerSubject, "PENDING");
        long cancelledOrders = orderRepository.countByManagerSubjectAndStatus(managerSubject, "CANCELLED");
        long refundedOrders = orderRepository.countByManagerSubjectAndStatus(managerSubject, "REFUNDED");
        long totalAmount = Math.max(0L, valueOrZero(orderRepository.sumTotalAmountByManagerSubjectAndStatus(managerSubject, "PAID")));
        Money totalSales = Money.of(totalAmount, "RUB");
        long averageAmount = paidOrders > 0 ? Math.round((double) totalAmount / paidOrders) : 0L;
        Money averageOrderValue = Money.of(averageAmount, "RUB");
        OffsetDateTime lastOrderAt = orderRepository.findTopByManagerSubjectOrderByOrderDateDesc(managerSubject)
                .map(Order::getOrderDate)
                .orElse(null);

        List<Order> recentOrders = orderRepository
                .findByManagerSubject(managerSubject, PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "orderDate")))
                .getContent();

        ManagerStats stats = new ManagerStats(
                totalOrders,
                paidOrders,
                pendingOrders,
                cancelledOrders,
                refundedOrders,
                totalSales,
                averageOrderValue,
                lastOrderAt
        );

        return ResponseEntity.ok(new ManagerDashboard(toProfile(identity), stats, recentOrders));
    }

    private long valueOrZero(Long value) {
        return value != null ? value : 0L;
    }

    private ManagerProfile toProfile(ManagerIdentity identity) {
        return new ManagerProfile(identity.subject(), identity.username(), identity.email(), identity.displayName());
    }

    private ManagerIdentity resolveManager(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Missing authentication token");
        }
        String subject = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String displayName = jwt.getClaimAsString("name");
        if (!StringUtils.hasText(displayName)) {
            String firstName = jwt.getClaimAsString("given_name");
            String lastName = jwt.getClaimAsString("family_name");
            if (StringUtils.hasText(firstName) || StringUtils.hasText(lastName)) {
                displayName = String.join(" ", List.of(
                        StringUtils.hasText(firstName) ? firstName : "",
                        StringUtils.hasText(lastName) ? lastName : ""
                )).trim();
            }
        }
        String managerSubject = StringUtils.hasText(subject)
                ? subject
                : StringUtils.hasText(username)
                ? username
                : email;
        if (!StringUtils.hasText(managerSubject)) {
            throw new IllegalArgumentException("Manager subject is required");
        }
        return new ManagerIdentity(managerSubject, username, email, displayName);
    }

    public record ManagerProfile(String subject, String username, String email, String displayName) {}

    public record ManagerIdentity(String subject, String username, String email, String displayName) {}

    public record ManagerStats(
            long totalOrders,
            long paidOrders,
            long pendingOrders,
            long cancelledOrders,
            long refundedOrders,
            Money totalSales,
            Money averageOrderValue,
            OffsetDateTime lastOrderAt
    ) {}

    public record ManagerDashboard(
            ManagerProfile manager,
            ManagerStats stats,
            List<Order> recentOrders
    ) {}
}
