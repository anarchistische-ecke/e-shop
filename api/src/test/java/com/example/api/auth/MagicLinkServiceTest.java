package com.example.api.auth;

import com.example.customer.domain.Customer;
import com.example.customer.service.CustomerService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MagicLinkServiceTest {
    private MockWebServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void reusesExistingKeycloakUserAndSendsMagicLink() throws Exception {
        MagicLinkService service = service();
        enqueueToken();
        enqueueJson("[{\"id\":\"user-1\"}]");
        enqueueJson("{\"id\":\"role-1\",\"name\":\"customer\"}");
        enqueueEmpty(204);
        enqueueEmpty(204);

        MagicLinkService.MagicLinkResult result =
                service.requestMagicLink("Customer@Example.Test", "https://shop.test/account#orders");

        assertThat(result.status()).isEqualTo(MagicLinkService.Status.ACCEPTED);
        assertThat(take().getPath()).isEqualTo("/realms/cozyhome/protocol/openid-connect/token");
        assertThat(take().getPath()).isEqualTo("/admin/realms/cozyhome/users?email=customer@example.test&exact=true");
        assertThat(take().getPath()).isEqualTo("/admin/realms/cozyhome/roles/customer");
        assertThat(take().getPath()).isEqualTo("/admin/realms/cozyhome/users/user-1/role-mappings/realm");
        RecordedRequest magicLinkRequest = take();
        assertThat(magicLinkRequest.getPath()).isEqualTo("/realms/cozyhome/magic-link");
        assertThat(magicLinkRequest.getBody().readUtf8()).contains(
                "\"email\":\"customer@example.test\"",
                "\"redirect_uri\":\"https://shop.test/account#orders\""
        );
    }

    @Test
    void createsKeycloakUserWhenEmailIsNew() throws Exception {
        MagicLinkService service = service();
        enqueueToken();
        enqueueJson("[]");
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Location", server.url("/admin/realms/cozyhome/users/user-2")));
        enqueueJson("{\"id\":\"role-1\",\"name\":\"customer\"}");
        enqueueEmpty(204);
        enqueueEmpty(204);

        MagicLinkService.MagicLinkResult result =
                service.requestMagicLink("customer@example.test", "https://shop.test/account#orders");

        assertThat(result.status()).isEqualTo(MagicLinkService.Status.ACCEPTED);
        take();
        take();
        RecordedRequest createUserRequest = take();
        assertThat(createUserRequest.getPath()).isEqualTo("/admin/realms/cozyhome/users");
        assertThat(createUserRequest.getBody().readUtf8()).contains(
                "\"username\":\"customer@example.test\"",
                "\"email\":\"customer@example.test\""
        );
    }

    @Test
    void rejectsRedirectsOutsideAllowedOriginsBeforeCallingKeycloak() throws Exception {
        MagicLinkService service = service();

        MagicLinkService.MagicLinkResult result =
                service.requestMagicLink("customer@example.test", "https://evil.test/account#orders");

        assertThat(result.status()).isEqualTo(MagicLinkService.Status.VALIDATION_ERROR);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void rateLimitsRepeatedRequestsForTheSameEmail() throws Exception {
        MagicLinkService service = service();
        enqueueToken();
        enqueueJson("[{\"id\":\"user-1\"}]");
        enqueueJson("{\"id\":\"role-1\",\"name\":\"customer\"}");
        enqueueEmpty(204);
        enqueueEmpty(204);

        MagicLinkService.MagicLinkResult first =
                service.requestMagicLink("customer@example.test", "https://shop.test/account#orders");
        MagicLinkService.MagicLinkResult second =
                service.requestMagicLink("customer@example.test", "https://shop.test/account#orders");

        assertThat(first.status()).isEqualTo(MagicLinkService.Status.ACCEPTED);
        assertThat(second.status()).isEqualTo(MagicLinkService.Status.RATE_LIMITED);
        assertThat(second.retryAt()).isNotNull();
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    private MagicLinkService service() throws Exception {
        server = new MockWebServer();
        server.start();
        MagicLinkProperties properties = new MagicLinkProperties();
        properties.setKeycloakBaseUrl(server.url("").toString());
        properties.setRealm("cozyhome");
        properties.setAdminClientId("magic-link-service");
        properties.setAdminClientSecret("secret");
        properties.setStorefrontClientId("cozyhome-web");
        properties.setCustomerRole("customer");
        properties.setCooldown(Duration.ofSeconds(60));
        properties.setAllowedRedirectOrigins(List.of("https://shop.test"));

        CustomerService customerService = mock(CustomerService.class);
        when(customerService.findOrCreateByEmail(eq("customer@example.test"), eq("Customer"), eq("")))
                .thenReturn(new Customer());

        return new MagicLinkService(
                properties,
                customerService,
                new RestTemplateBuilder()
        );
    }

    private void enqueueToken() {
        enqueueJson("{\"access_token\":\"admin-token\"}");
    }

    private void enqueueJson(String json) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(json));
    }

    private void enqueueEmpty(int status) {
        server.enqueue(new MockResponse().setResponseCode(status));
    }

    private RecordedRequest take() throws InterruptedException {
        return server.takeRequest();
    }
}
