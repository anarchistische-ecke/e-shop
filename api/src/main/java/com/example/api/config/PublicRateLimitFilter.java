package com.example.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final long PRUNE_INTERVAL_MILLIS = Duration.ofMinutes(5).toMillis();

    private final ConcurrentMap<String, FixedWindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastPruneAt = new AtomicLong(System.currentTimeMillis());

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.checkout.limit:10}")
    private int checkoutLimit;

    @Value("${app.rate-limit.checkout.window-seconds:60}")
    private int checkoutWindowSeconds;

    @Value("${app.rate-limit.cart.limit:90}")
    private int cartLimit;

    @Value("${app.rate-limit.cart.window-seconds:60}")
    private int cartWindowSeconds;

    @Value("${app.rate-limit.order-token.limit:30}")
    private int orderTokenLimit;

    @Value("${app.rate-limit.order-token.window-seconds:60}")
    private int orderTokenWindowSeconds;

    @Value("${app.rate-limit.webhook.limit:120}")
    private int webhookLimit;

    @Value("${app.rate-limit.webhook.window-seconds:60}")
    private int webhookWindowSeconds;

    @Value("${app.rate-limit.privileged.limit:120}")
    private int privilegedLimit;

    @Value("${app.rate-limit.privileged.window-seconds:60}")
    private int privilegedWindowSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RatePolicy policy = resolvePolicy(request);
        if (!enabled || policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        pruneExpiredCounters(now);
        String key = policy.name() + ":" + policy.scope();
        FixedWindowCounter counter = counters.computeIfAbsent(key, ignored -> new FixedWindowCounter(now));
        RateLimitResult result = counter.tryAcquire(policy, now);
        if (result.allowed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit()));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setContentType("application/json");
        response.getWriter().write("""
                {"code":"RATE_LIMITED","message":"Too many requests","recoverable":true,"fieldErrors":[]}""");
    }

    private RatePolicy resolvePolicy(HttpServletRequest request) {
        String path = normalizePath(request.getRequestURI());
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        if (path.equals("/orders/checkout") && method.equals(HttpMethod.POST.name())) {
            return new RatePolicy("checkout", checkoutLimit, checkoutWindowSeconds, clientAddress(request));
        }
        if (path.equals("/orders") && method.equals(HttpMethod.POST.name())) {
            return new RatePolicy("checkout", checkoutLimit, checkoutWindowSeconds, clientAddress(request));
        }
        if (path.startsWith("/orders/public/") && method.equals(HttpMethod.GET.name())) {
            return new RatePolicy("order-token-read", orderTokenLimit, orderTokenWindowSeconds, clientAddress(request));
        }
        if (path.endsWith("/refresh-payment") && path.startsWith("/orders/public/")
                && method.equals(HttpMethod.POST.name())) {
            return new RatePolicy("order-token-refresh", orderTokenLimit, orderTokenWindowSeconds, clientAddress(request));
        }
        if (path.endsWith("/pay") && path.startsWith("/orders/public/")
                && method.equals(HttpMethod.POST.name())) {
            return new RatePolicy("order-token-pay", orderTokenLimit, orderTokenWindowSeconds, clientAddress(request));
        }
        if (path.equals("/payments/yookassa/webhook") && method.equals(HttpMethod.POST.name())) {
            return new RatePolicy("payment-webhook", webhookLimit, webhookWindowSeconds, clientAddress(request));
        }
        if (path.equals("/carts") && method.equals(HttpMethod.POST.name())) {
            return new RatePolicy("cart-create", cartLimit, cartWindowSeconds, clientAddress(request));
        }
        if (path.startsWith("/carts/") && isMutatingCartMethod(method)) {
            return new RatePolicy("cart-write", cartLimit, cartWindowSeconds, cartScope(path, request));
        }
        if (isPrivilegedSurface(path, method)) {
            return new RatePolicy("privileged", privilegedLimit, privilegedWindowSeconds, clientAddress(request));
        }
        return null;
    }

    private boolean isMutatingCartMethod(String method) {
        return method.equals(HttpMethod.POST.name())
                || method.equals(HttpMethod.PUT.name())
                || method.equals(HttpMethod.DELETE.name());
    }

    private String cartScope(String path, HttpServletRequest request) {
        String prefix = "/carts/";
        if (!path.startsWith(prefix)) {
            return clientAddress(request);
        }
        String remainder = path.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        String cartId = slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder;
        if (!StringUtils.hasText(cartId)) {
            return clientAddress(request);
        }
        return "cart:" + cartId;
    }

    private boolean isPrivilegedSurface(String path, String method) {
        if (path.equals("/admin") || path.startsWith("/admin/")) {
            return true;
        }
        if (path.equals("/internal/directus") || path.startsWith("/internal/directus/")) {
            return true;
        }
        if (path.equals("/managers") || path.startsWith("/managers/")) {
            return true;
        }
        if (path.equals("/orders/admin-link") || path.equals("/orders/manager-link")) {
            return true;
        }
        if (path.equals("/payments/yookassa/refund") || path.equals("/payments/yookassa/cancel")) {
            return true;
        }
        if (method.equals(HttpMethod.GET.name())) {
            return false;
        }
        return path.equals("/products") || path.startsWith("/products/")
                || path.equals("/categories") || path.startsWith("/categories/")
                || path.equals("/brands") || path.startsWith("/brands/")
                || path.equals("/inventory") || path.startsWith("/inventory/");
    }

    private String normalizePath(String requestUri) {
        if (!StringUtils.hasText(requestUri)) {
            return "/";
        }
        int queryStart = requestUri.indexOf('?');
        return queryStart >= 0 ? requestUri.substring(0, queryStart) : requestUri;
    }

    private String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String firstHop = forwardedFor.split(",", 2)[0].trim();
            if (StringUtils.hasText(firstHop)) {
                return firstHop;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void pruneExpiredCounters(long now) {
        long lastPrune = lastPruneAt.get();
        if (now - lastPrune < PRUNE_INTERVAL_MILLIS || !lastPruneAt.compareAndSet(lastPrune, now)) {
            return;
        }
        Iterator<Map.Entry<String, FixedWindowCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, FixedWindowCounter> entry = iterator.next();
            if (entry.getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private record RatePolicy(String name, int limit, int windowSeconds, String scope) {
        private RatePolicy {
            limit = Math.max(1, limit);
            windowSeconds = Math.max(1, windowSeconds);
            scope = StringUtils.hasText(scope) ? scope : "unknown";
        }
    }

    private record RateLimitResult(boolean allowed, int remaining, long retryAfterSeconds) {
    }

    private static final class FixedWindowCounter {
        private long windowStartedAt;
        private int count;

        private FixedWindowCounter(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

        private synchronized RateLimitResult tryAcquire(RatePolicy policy, long now) {
            long windowMillis = policy.windowSeconds() * 1000L;
            if (now - windowStartedAt >= windowMillis) {
                windowStartedAt = now;
                count = 0;
            }
            if (count >= policy.limit()) {
                long retryAfterMillis = Math.max(1000L, windowMillis - (now - windowStartedAt));
                return new RateLimitResult(false, 0, (retryAfterMillis + 999L) / 1000L);
            }
            count++;
            return new RateLimitResult(true, policy.limit() - count, 0L);
        }

        private synchronized boolean isExpired(long now) {
            return now - windowStartedAt > PRUNE_INTERVAL_MILLIS;
        }
    }
}
