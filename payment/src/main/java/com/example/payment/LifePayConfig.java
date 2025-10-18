package com.example.payment;

import com.example.payment.service.LifePayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(LifePayProperties.class)
public class LifePayConfig {

    @Bean
    @ConditionalOnProperty(prefix = "lifepay", name = "enabled", havingValue = "true")
    public RestTemplate lifePayRestTemplate(RestTemplateBuilder builder, LifePayProperties props) {
        return builder
                .rootUri(props.getBaseUrl())
                .setConnectTimeout(props.getConnectTimeout())
                .setReadTimeout(props.getReadTimeout())
                .additionalInterceptors((request, body, execution) -> {
                    // Add Bearer token once for all requests
                    request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "lifepay", name = "enabled", havingValue = "true")
    public LifePayClient lifePayClient(RestTemplate lifePayRestTemplate) {
        return new LifePayClient(lifePayRestTemplate);
    }
}


