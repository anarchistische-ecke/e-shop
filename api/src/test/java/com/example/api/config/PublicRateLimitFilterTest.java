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

    private FilterChain terminalChain() {
        return (request, response) -> ((HttpServletResponse) response).setStatus(204);
    }
}
