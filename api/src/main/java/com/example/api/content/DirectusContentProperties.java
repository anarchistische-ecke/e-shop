package com.example.api.content;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "directus")
public class DirectusContentProperties {

    private String baseUrl;
    private String publicUrl;
    private String staticToken;
    private Duration cacheTtl = Duration.ofMinutes(5);
    private Duration cacheStaleTtl = Duration.ofHours(1);
    private String cacheKeyPrefix = "cms:content";
    private Duration responseCacheMaxAge = Duration.ofMinutes(1);
    private Duration responseCacheStaleWhileRevalidate = Duration.ofMinutes(5);
    private Duration responseCacheStaleIfError = Duration.ofHours(1);
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration slowRequestThreshold = Duration.ofSeconds(2);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getStaticToken() {
        return staticToken;
    }

    public void setStaticToken(String staticToken) {
        this.staticToken = staticToken;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public Duration getCacheStaleTtl() {
        return cacheStaleTtl;
    }

    public void setCacheStaleTtl(Duration cacheStaleTtl) {
        this.cacheStaleTtl = cacheStaleTtl;
    }

    public String getCacheKeyPrefix() {
        return cacheKeyPrefix;
    }

    public void setCacheKeyPrefix(String cacheKeyPrefix) {
        this.cacheKeyPrefix = cacheKeyPrefix;
    }

    public Duration getResponseCacheMaxAge() {
        return responseCacheMaxAge;
    }

    public void setResponseCacheMaxAge(Duration responseCacheMaxAge) {
        this.responseCacheMaxAge = responseCacheMaxAge;
    }

    public Duration getResponseCacheStaleWhileRevalidate() {
        return responseCacheStaleWhileRevalidate;
    }

    public void setResponseCacheStaleWhileRevalidate(Duration responseCacheStaleWhileRevalidate) {
        this.responseCacheStaleWhileRevalidate = responseCacheStaleWhileRevalidate;
    }

    public Duration getResponseCacheStaleIfError() {
        return responseCacheStaleIfError;
    }

    public void setResponseCacheStaleIfError(Duration responseCacheStaleIfError) {
        this.responseCacheStaleIfError = responseCacheStaleIfError;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getSlowRequestThreshold() {
        return slowRequestThreshold;
    }

    public void setSlowRequestThreshold(Duration slowRequestThreshold) {
        this.slowRequestThreshold = slowRequestThreshold;
    }
}
