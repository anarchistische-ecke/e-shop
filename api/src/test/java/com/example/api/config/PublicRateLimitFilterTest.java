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

    @Test
    void chatConversationStartsAreRateLimitedSeparatelyFromMessages() throws Exception {
        PublicRateLimitFilter filter = configuredFilter("chatStartLimit", 1, "chatStartWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "chatMessageLimit", 1);
        ReflectionTestUtils.setField(filter, "chatMessageWindowSeconds", 60);

        MockHttpServletRequest startRequest = new MockHttpServletRequest("POST", "/chat/conversations");
        startRequest.addHeader("X-Forwarded-For", "203.0.113.30");
        MockHttpServletResponse startResponse = new MockHttpServletResponse();
        filter.doFilter(startRequest, startResponse, terminalChain());

        MockHttpServletRequest messageRequest = new MockHttpServletRequest("POST", "/chat/conversations/00000000-0000-0000-0000-000000000001/messages");
        messageRequest.addHeader("X-Forwarded-For", "203.0.113.30");
        MockHttpServletResponse messageResponse = new MockHttpServletResponse();
        filter.doFilter(messageRequest, messageResponse, terminalChain());

        MockHttpServletRequest secondStartRequest = new MockHttpServletRequest("POST", "/chat/conversations");
        secondStartRequest.addHeader("X-Forwarded-For", "203.0.113.30");
        MockHttpServletResponse secondStartResponse = new MockHttpServletResponse();
        filter.doFilter(secondStartRequest, secondStartResponse, terminalChain());

        assertThat(startResponse.getStatus()).isEqualTo(204);
        assertThat(messageResponse.getStatus()).isEqualTo(204);
        assertThat(secondStartResponse.getStatus()).isEqualTo(429);
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

    private FilterChain terminalChain() {
        return (request, response) -> ((HttpServletResponse) response).setStatus(204);
    }
}
