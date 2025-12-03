package com.example.api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class SocialAuthService {
    private static final Logger log = LoggerFactory.getLogger(SocialAuthService.class);

    private final RestTemplate restTemplate;

    public SocialAuthService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(4))
                .setReadTimeout(Duration.ofSeconds(4))
                .build();
    }

    public SocialProfile fetchYandexProfile(String accessToken, String fallbackId, String fallbackEmail, String fallbackFirstName, String fallbackLastName) {
        String id = fallbackId;
        String email = fallbackEmail;
        String firstName = fallbackFirstName;
        String lastName = fallbackLastName;

        if (StringUtils.hasText(accessToken)) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "OAuth " + accessToken);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<YandexInfoResponse> response = restTemplate.exchange(
                        "https://login.yandex.ru/info?format=json",
                        HttpMethod.GET,
                        entity,
                        YandexInfoResponse.class
                );
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    YandexInfoResponse body = response.getBody();
                    id = firstNonBlank(body.getId(), id);
                    email = firstNonBlank(body.getDefaultEmail(), email);
                    if (!StringUtils.hasText(email) && body.getEmails() != null && !body.getEmails().isEmpty()) {
                        email = body.getEmails().get(0);
                    }
                    firstName = firstNonBlank(body.getFirstName(), firstName);
                    lastName = firstNonBlank(body.getLastName(), lastName);
                }
            } catch (RestClientException ex) {
                log.warn("Failed to verify Yandex token: {}", ex.getMessage());
            }
        }

        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Yandex ID is required");
        }
        if (!StringUtils.hasText(email)) {
            email = id + "@yandex.local";
        }

        return new SocialProfile(
                "yandex",
                id,
                email,
                defaultName(firstName, "Yandex"),
                defaultName(lastName, "User")
        );
    }

    public SocialProfile fetchVkProfile(String accessToken, String fallbackId, String fallbackEmail, String fallbackFirstName, String fallbackLastName) {
        String id = fallbackId;
        String email = fallbackEmail;
        String firstName = fallbackFirstName;
        String lastName = fallbackLastName;

        if (StringUtils.hasText(accessToken)) {
            try {
                var uri = UriComponentsBuilder
                        .fromHttpUrl("https://api.vk.com/method/users.get")
                        .queryParam("access_token", accessToken)
                        .queryParam("v", "5.199")
                        .queryParam("fields", "first_name,last_name")
                        .build()
                        .toUri();
                ResponseEntity<VkUsersResponse> response = restTemplate.getForEntity(uri, VkUsersResponse.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().getResponse() != null) {
                    List<VkUser> users = response.getBody().getResponse();
                    if (!users.isEmpty()) {
                        VkUser user = users.get(0);
                        id = firstNonBlank(user.getId() != null ? user.getId().toString() : null, id);
                        firstName = firstNonBlank(user.getFirstName(), firstName);
                        lastName = firstNonBlank(user.getLastName(), lastName);
                    }
                }
            } catch (RestClientException ex) {
                log.warn("Failed to verify VK token: {}", ex.getMessage());
            }
        }

        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("VK user id is required");
        }
        if (!StringUtils.hasText(email)) {
            email = "vk-" + id + "@vk.local";
        }

        return new SocialProfile(
                "vk",
                id,
                email,
                defaultName(firstName, "VK"),
                defaultName(lastName, "User")
        );
    }

    private String firstNonBlank(String candidate, String fallback) {
        return StringUtils.hasText(candidate) ? candidate : fallback;
    }

    private String defaultName(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record SocialProfile(String provider, String externalId, String email, String firstName, String lastName) { }

    public static class YandexInfoResponse {
        private String id;
        @JsonProperty("default_email")
        private String defaultEmail;
        private String firstName;
        private String lastName;
        private List<String> emails = Collections.emptyList();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDefaultEmail() {
            return defaultEmail;
        }

        public void setDefaultEmail(String defaultEmail) {
            this.defaultEmail = defaultEmail;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public List<String> getEmails() {
            return emails;
        }

        public void setEmails(List<String> emails) {
            this.emails = emails;
        }
    }

    public static class VkUsersResponse {
        private List<VkUser> response;

        public List<VkUser> getResponse() {
            return response;
        }

        public void setResponse(List<VkUser> response) {
            this.response = response;
        }
    }

    public static class VkUser {
        private Long id;
        @JsonProperty("first_name")
        private String firstName;
        @JsonProperty("last_name")
        private String lastName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }
}
