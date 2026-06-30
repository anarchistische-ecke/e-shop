package com.example.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "auth.magic-link")
public class MagicLinkProperties {
    private boolean enabled = true;
    private String keycloakBaseUrl = "http://localhost:8081";
    private String realm = "cozyhome";
    private String adminClientId = "magic-link-service";
    private String adminClientSecret = "";
    private String storefrontClientId = "cozyhome-web";
    private String customerRole = "customer";
    private Duration lifespan = Duration.ofMinutes(15);
    private Duration cooldown = Duration.ofSeconds(60);
    private List<String> allowedRedirectOrigins = new ArrayList<>(List.of("http://localhost:3000"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeycloakBaseUrl() {
        return keycloakBaseUrl;
    }

    public void setKeycloakBaseUrl(String keycloakBaseUrl) {
        this.keycloakBaseUrl = keycloakBaseUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getAdminClientId() {
        return adminClientId;
    }

    public void setAdminClientId(String adminClientId) {
        this.adminClientId = adminClientId;
    }

    public String getAdminClientSecret() {
        return adminClientSecret;
    }

    public void setAdminClientSecret(String adminClientSecret) {
        this.adminClientSecret = adminClientSecret;
    }

    public String getStorefrontClientId() {
        return storefrontClientId;
    }

    public void setStorefrontClientId(String storefrontClientId) {
        this.storefrontClientId = storefrontClientId;
    }

    public String getCustomerRole() {
        return customerRole;
    }

    public void setCustomerRole(String customerRole) {
        this.customerRole = customerRole;
    }

    public Duration getLifespan() {
        return lifespan;
    }

    public void setLifespan(Duration lifespan) {
        this.lifespan = lifespan;
    }

    public Duration getCooldown() {
        return cooldown;
    }

    public void setCooldown(Duration cooldown) {
        this.cooldown = cooldown;
    }

    public List<String> getAllowedRedirectOrigins() {
        return allowedRedirectOrigins;
    }

    public void setAllowedRedirectOrigins(List<String> allowedRedirectOrigins) {
        this.allowedRedirectOrigins = allowedRedirectOrigins;
    }
}
