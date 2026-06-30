package com.example.api.auth;

import com.example.customer.service.CustomerService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MagicLinkService {
    private final MagicLinkProperties properties;
    private final CustomerService customerService;
    private final RestTemplate restTemplate;
    private final Map<String, Instant> lastRequestByEmail = new ConcurrentHashMap<>();

    public MagicLinkService(MagicLinkProperties properties,
                            CustomerService customerService,
                            RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.customerService = customerService;
        this.restTemplate = restTemplateBuilder.build();
    }

    public MagicLinkResult requestMagicLink(String email, String redirectUri) {
        if (!properties.isEnabled()) {
            return MagicLinkResult.unavailable("Magic-link login is disabled");
        }
        String normalizedEmail = normalizeEmail(email);
        if (!isValidEmail(normalizedEmail)) {
            return MagicLinkResult.validationError("Valid email is required");
        }
        if (!isAllowedRedirectUri(redirectUri)) {
            return MagicLinkResult.validationError("Redirect URI is not allowed");
        }
        Optional<Instant> retryAt = resolveRetryAt(normalizedEmail);
        if (retryAt.isPresent()) {
            return MagicLinkResult.rateLimited(retryAt.get());
        }

        customerService.findOrCreateByEmail(normalizedEmail, "Customer", "");
        String token = requestAdminToken();
        String userId = ensureKeycloakUser(token, normalizedEmail);
        assignCustomerRole(token, userId);
        sendMagicLink(token, normalizedEmail, redirectUri);
        lastRequestByEmail.put(normalizedEmail, Instant.now());
        return MagicLinkResult.accepted();
    }

    private Optional<Instant> resolveRetryAt(String email) {
        Instant lastRequest = lastRequestByEmail.get(email);
        if (lastRequest == null) {
            return Optional.empty();
        }
        Instant retryAt = lastRequest.plus(properties.getCooldown());
        return retryAt.isAfter(Instant.now()) ? Optional.of(retryAt) : Optional.empty();
    }

    private String requestAdminToken() {
        if (!StringUtils.hasText(properties.getAdminClientSecret())) {
            throw new IllegalStateException("Magic-link Keycloak client secret is not configured");
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", properties.getAdminClientId());
        body.add("client_secret", properties.getAdminClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                keycloakRealmUrl("/protocol/openid-connect/token"),
                new HttpEntity<>(body, headers),
                Map.class
        );
        Object accessToken = response.getBody() != null ? response.getBody().get("access_token") : null;
        if (accessToken == null || !StringUtils.hasText(String.valueOf(accessToken))) {
            throw new IllegalStateException("Keycloak admin token response did not include access_token");
        }
        return String.valueOf(accessToken);
    }

    private String ensureKeycloakUser(String token, String email) {
        Optional<String> existingUserId = findKeycloakUserId(token, email);
        if (existingUserId.isPresent()) {
            return existingUserId.get();
        }

        HttpHeaders headers = authJsonHeaders(token);
        Map<String, Object> body = Map.of(
                "username", email,
                "email", email,
                "enabled", true,
                "emailVerified", true
        );
        ResponseEntity<Void> response = restTemplate.postForEntity(
                keycloakAdminUrl("/users"),
                new HttpEntity<>(body, headers),
                Void.class
        );
        URI location = response.getHeaders().getLocation();
        if (location != null) {
            String path = location.getPath();
            int index = path.lastIndexOf('/');
            if (index >= 0 && index < path.length() - 1) {
                return path.substring(index + 1);
            }
        }
        return findKeycloakUserId(token, email)
                .orElseThrow(() -> new IllegalStateException("Created Keycloak user could not be resolved"));
    }

    private Optional<String> findKeycloakUserId(String token, String email) {
        HttpHeaders headers = authJsonHeaders(token);
        String query = "?email=" + UriUtils.encodeQueryParam(email, StandardCharsets.UTF_8)
                + "&exact=true";
        ResponseEntity<List> response = restTemplate.exchange(
                keycloakAdminUrl("/users" + query),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class
        );
        List<?> users = response.getBody();
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }
        Object first = users.get(0);
        if (first instanceof Map<?, ?> user) {
            Object id = user.get("id");
            if (StringUtils.hasText(String.valueOf(id))) {
                return Optional.of(String.valueOf(id));
            }
        }
        return Optional.empty();
    }

    private void assignCustomerRole(String token, String userId) {
        if (!StringUtils.hasText(properties.getCustomerRole())) {
            return;
        }
        HttpHeaders headers = authJsonHeaders(token);
        ResponseEntity<Map> roleResponse = restTemplate.exchange(
                keycloakAdminUrl("/roles/" + UriUtils.encodePathSegment(properties.getCustomerRole(), StandardCharsets.UTF_8)),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        Map<?, ?> role = roleResponse.getBody();
        if (role == null || role.isEmpty()) {
            throw new IllegalStateException("Customer role is not configured in Keycloak");
        }
        restTemplate.postForEntity(
                keycloakAdminUrl("/users/" + UriUtils.encodePathSegment(userId, StandardCharsets.UTF_8) + "/role-mappings/realm"),
                new HttpEntity<>(List.of(role), headers),
                Void.class
        );
    }

    private void sendMagicLink(String token, String email, String redirectUri) {
        HttpHeaders headers = authJsonHeaders(token);
        Map<String, Object> body = Map.of(
                "email", email,
                "client_id", properties.getStorefrontClientId(),
                "redirect_uri", redirectUri,
                "expiration_seconds", Math.max(60, properties.getLifespan().toSeconds()),
                "send_email", true,
                "force_create", false
        );
        restTemplate.postForEntity(
                keycloakRealmUrl("/magic-link"),
                new HttpEntity<>(body, headers),
                Void.class
        );
    }

    private HttpHeaders authJsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private boolean isAllowedRedirectUri(String redirectUri) {
        if (!StringUtils.hasText(redirectUri)) {
            return false;
        }
        try {
            URI parsed = URI.create(redirectUri);
            String scheme = parsed.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            if (StringUtils.hasText(parsed.getUserInfo())) {
                return false;
            }
            String origin = parsed.getScheme() + "://" + parsed.getAuthority();
            List<String> allowedOrigins = properties.getAllowedRedirectOrigins() == null
                    ? List.of()
                    : properties.getAllowedRedirectOrigins();
            return allowedOrigins.stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .anyMatch(origin::equalsIgnoreCase);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String keycloakRealmUrl(String path) {
        return normalizeBaseUrl(properties.getKeycloakBaseUrl())
                + "/realms/"
                + UriUtils.encodePathSegment(properties.getRealm(), StandardCharsets.UTF_8)
                + path;
    }

    private String keycloakAdminUrl(String path) {
        return normalizeBaseUrl(properties.getKeycloakBaseUrl())
                + "/admin/realms/"
                + UriUtils.encodePathSegment(properties.getRealm(), StandardCharsets.UTF_8)
                + path;
    }

    private String normalizeBaseUrl(String value) {
        String configured = StringUtils.hasText(value) ? value : "http://localhost:8081";
        return configured.replaceAll("/+$", "");
    }

    private String normalizeEmail(String email) {
        return String.valueOf(email).trim().toLowerCase(Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        return StringUtils.hasText(email) && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    public record MagicLinkResult(Status status, Instant retryAt, String message) {
        public static MagicLinkResult accepted() {
            return new MagicLinkResult(Status.ACCEPTED, null, "");
        }

        public static MagicLinkResult validationError(String message) {
            return new MagicLinkResult(Status.VALIDATION_ERROR, null, message);
        }

        public static MagicLinkResult unavailable(String message) {
            return new MagicLinkResult(Status.UNAVAILABLE, null, message);
        }

        public static MagicLinkResult rateLimited(Instant retryAt) {
            return new MagicLinkResult(Status.RATE_LIMITED, retryAt, "Please wait before requesting another link");
        }
    }

    public enum Status {
        ACCEPTED,
        VALIDATION_ERROR,
        RATE_LIMITED,
        UNAVAILABLE
    }
}
