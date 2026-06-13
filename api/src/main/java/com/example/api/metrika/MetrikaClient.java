package com.example.api.metrika;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Service
public class MetrikaClient {
    private final RestTemplate restTemplate;
    private final MetrikaProperties properties;

    public MetrikaClient(RestTemplate restTemplate, MetrikaProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public void uploadOfflineConversion(String csvPayload) {
        if (!properties.isEnabled() || !properties.getOfflineImport().isEnabled()) {
            throw new IllegalStateException("Metrika offline import is disabled");
        }
        if (!StringUtils.hasText(properties.getCounterId())) {
            throw new IllegalStateException("Yandex Metrica counter id is not configured");
        }
        if (!StringUtils.hasText(properties.getOauthToken())) {
            throw new IllegalStateException("Yandex Metrica OAuth token is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getOauthToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(csvPayload.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "metrika-offline-conversions.csv";
            }
        });

        restTemplate.postForEntity(resolveUploadUrl(), new HttpEntity<>(body, headers), String.class);
    }

    private String resolveUploadUrl() {
        String configuredUrl = properties.getOfflineImport().getUrl();
        if (StringUtils.hasText(configuredUrl)) {
            return configuredUrl.replace("{counterId}", properties.getCounterId().trim());
        }
        return "https://api-metrika.yandex.net/management/v1/counter/"
                + properties.getCounterId().trim()
                + "/offline_conversions/upload";
    }
}
