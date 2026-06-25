package com.example.api.catalog.media;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class MediaUploadFeatureFlag {
    public static final String REDIS_KEY = "catalogue:media:uploads-enabled";

    private final StringRedisTemplate redis;
    private final MediaUploadProperties properties;

    public MediaUploadFeatureFlag(StringRedisTemplate redis, MediaUploadProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean isEnabled() {
        String override = redis.opsForValue().get(REDIS_KEY);
        return override == null ? properties.isEnabled() : Boolean.parseBoolean(override);
    }

    public boolean setEnabled(boolean enabled) {
        redis.opsForValue().set(REDIS_KEY, String.valueOf(enabled));
        return enabled;
    }
}
