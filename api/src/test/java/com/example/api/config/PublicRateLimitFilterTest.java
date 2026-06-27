package com.example.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRateLimitFilterTest {

    @Test
    void checkoutRequestsAreRateLimitedPerForwardedClientAddress() throws Exception {
        PublicRateLimitFilter filter = configuredFilter("checkoutLimit", 1, "checkoutWindowSeconds", 60);

        MockHttpServletRequest firstRequest = checkoutRequest("203.0.113.10");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, terminalChain());

        MockHttpServletRequest secondRequest = checkoutRequest("203.0.113.10");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, terminalChain());

        assertThat(firstResponse.getStatus()).isEqualTo(204);
        assertThat(firstResponse.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(secondResponse.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void unrelatedReadEndpointsAreNotLimited() throws Exception {
        PublicRateLimitFilter filter = configuredFilter("checkoutLimit", 1, "checkoutWindowSeconds", 60);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, terminalChain());

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }

    @Test
    void cartReadsDoNotConsumeCartWriteLimit() throws Exception {
        PublicRateLimitFilter filter = configuredFilter("cartLimit", 1, "cartWindowSeconds", 60);

        MockHttpServletRequest readRequest = cartRequest("GET", "/carts/11111111-1111-1111-1111-111111111111", "203.0.113.30");
        MockHttpServletResponse readResponse = new MockHttpServletResponse();
        filter.doFilter(readRequest, readResponse, terminalChain());

        MockHttpServletRequest writeRequest = cartRequest("POST", "/carts/11111111-1111-1111-1111-111111111111/items", "203.0.113.30");
        MockHttpServletResponse writeResponse = new MockHttpServletResponse();
        filter.doFilter(writeRequest, writeResponse, terminalChain());

        assertThat(readResponse.getStatus()).isEqualTo(204);
        assertThat(readResponse.getHeader("X-RateLimit-Limit")).isNull();
        assertThat(writeResponse.getStatus()).isEqualTo(204);
        assertThat(writeResponse.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void cartWritesAreRateLimitedPerCartId() throws Exception {
        PublicRateLimitFilter filter = configuredFilter("cartLimit", 1, "cartWindowSeconds", 60);

        MockHttpServletRequest firstCartRequest = cartRequest("POST", "/carts/11111111-1111-1111-1111-111111111111/items", "203.0.113.40");
        MockHttpServletResponse firstCartResponse = new MockHttpServletResponse();
        filter.doFilter(firstCartRequest, firstCartResponse, terminalChain());

        MockHttpServletRequest sameCartRequest = cartRequest("POST", "/carts/11111111-1111-1111-1111-111111111111/items", "203.0.113.40");
        MockHttpServletResponse sameCartResponse = new MockHttpServletResponse();
        filter.doFilter(sameCartRequest, sameCartResponse, terminalChain());

        MockHttpServletRequest otherCartRequest = cartRequest("POST", "/carts/22222222-2222-2222-2222-222222222222/items", "203.0.113.40");
        MockHttpServletResponse otherCartResponse = new MockHttpServletResponse();
        filter.doFilter(otherCartRequest, otherCartResponse, terminalChain());

        assertThat(firstCartResponse.getStatus()).isEqualTo(204);
        assertThat(sameCartResponse.getStatus()).isEqualTo(429);
        assertThat(otherCartResponse.getStatus()).isEqualTo(204);
    }

    @Test
    void cartCreationIsStillRateLimitedPerForwardedClientAddress() throws Exception {
        PublicRateLimitFilter filter = configuredFilter("cartLimit", 1, "cartWindowSeconds", 60);

        MockHttpServletRequest firstRequest = cartRequest("POST", "/carts", "203.0.113.50");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, terminalChain());

        MockHttpServletRequest secondRequest = cartRequest("POST", "/carts", "203.0.113.50");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, terminalChain());

        assertThat(firstResponse.getStatus()).isEqualTo(204);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void publicOrderRefreshDoesNotConsumePublicOrderReadLimit() throws Exception {
        PublicRateLimitFilter filter = configuredFilter("orderTokenLimit", 1, "orderTokenWindowSeconds", 60);

        MockHttpServletRequest refreshRequest = publicOrderRequest("POST", "/orders/public/public-token/refresh-payment");
        MockHttpServletResponse refreshResponse = new MockHttpServletResponse();
        filter.doFilter(refreshRequest, refreshResponse, terminalChain());

        MockHttpServletRequest readRequest = publicOrderRequest("GET", "/orders/public/public-token");
        MockHttpServletResponse readResponse = new MockHttpServletResponse();
        filter.doFilter(readRequest, readResponse, terminalChain());

        assertThat(refreshResponse.getStatus()).isEqualTo(204);
        assertThat(refreshResponse.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(readResponse.getStatus()).isEqualTo(204);
        assertThat(readResponse.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void publicOrderPayDoesNotConsumePublicOrderReadLimit() throws Exception {
        PublicRateLimitFilter filter = configuredFilter("orderTokenLimit", 1, "orderTokenWindowSeconds", 60);

        MockHttpServletRequest payRequest = publicOrderRequest("POST", "/orders/public/public-token/pay");
        MockHttpServletResponse payResponse = new MockHttpServletResponse();
        filter.doFilter(payRequest, payResponse, terminalChain());

        MockHttpServletRequest readRequest = publicOrderRequest("GET", "/orders/public/public-token");
        MockHttpServletResponse readResponse = new MockHttpServletResponse();
        filter.doFilter(readRequest, readResponse, terminalChain());

        assertThat(payResponse.getStatus()).isEqualTo(204);
        assertThat(payResponse.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(readResponse.getStatus()).isEqualTo(204);
        assertThat(readResponse.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    private PublicRateLimitFilter configuredFilter(String limitField,
                                                  int limit,
                                                  String windowField,
                                                  int windowSeconds) {
        PublicRateLimitFilter filter = new PublicRateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, limitField, limit);
        ReflectionTestUtils.setField(filter, windowField, windowSeconds);
        return filter;
    }

    private MockHttpServletRequest checkoutRequest(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders/checkout");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    private MockHttpServletRequest publicOrderRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Forwarded-For", "203.0.113.20");
        return request;
    }

    private MockHttpServletRequest cartRequest(String method, String path, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    private FilterChain terminalChain() {
        return (request, response) -> ((HttpServletResponse) response).setStatus(204);
    }
}
