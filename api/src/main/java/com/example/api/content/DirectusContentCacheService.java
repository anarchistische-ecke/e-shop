package com.example.api.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class DirectusContentCacheService {

    private static final Logger log = LoggerFactory.getLogger(DirectusContentCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DirectusContentProperties properties;
    private final CmsObservabilityService observabilityService;

    public DirectusContentCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            DirectusContentProperties properties,
            CmsObservabilityService observabilityService
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.observabilityService = observabilityService;
    }

    public <T> T getOrLoad(String cacheKey, TypeReference<T> typeReference, Supplier<T> loader) {
        if (!isCacheEnabled()) {
            observabilityService.recordCacheLookup(cacheKey, "bypass");
            return loader.get();
        }

        String redisKey = namespaced(cacheKey);
        String cachedPayload = read(cacheKey, redisKey);
        if (StringUtils.hasText(cachedPayload)) {
            try {
                T value = objectMapper.readValue(cachedPayload, typeReference);
                observabilityService.recordCacheLookup(cacheKey, "hit");
                return value;
            } catch (IOException ex) {
                observabilityService.recordCacheLookup(cacheKey, "deserialize_error");
                log.warn("Failed to deserialize CMS cache entry {}, evicting stale payload", redisKey, ex);
                safeDelete(List.of(redisKey));
            }
        }

        observabilityService.recordCacheLookup(cacheKey, "miss");
        T loaded = loader.get();
        write(cacheKey, redisKey, loaded);
        return loaded;
    }

    public CacheInvalidationResult invalidateAll() {
        return invalidateByPatterns("all", List.of("*"));
    }

    public CacheInvalidationResult invalidateSiteSettings() {
        return invalidateByKeys("site_settings", List.of(ContentCacheKeys.siteSettings()));
    }

    public CacheInvalidationResult invalidateNavigation(String placement) {
        if (StringUtils.hasText(placement)) {
            return invalidateByKeys(
                    "navigation",
                    List.of(ContentCacheKeys.navigationAll(), ContentCacheKeys.navigation(placement))
            );
        }

        return invalidateByPatterns("navigation", List.of("navigation:*"));
    }

    public CacheInvalidationResult invalidatePage(String slug) {
        return invalidateByKeys("page", List.of(ContentCacheKeys.page(slug)));
    }

    public record CacheInvalidationResult(
            String scope,
            String keyPrefix,
            List<String> selectors,
            long deletedKeys
    ) {
    }

    private String read(String cacheKey, String redisKey) {
        try {
            return redisTemplate.opsForValue().get(redisKey);
        } catch (DataAccessException ex) {
            observabilityService.recordCacheLookup(cacheKey, "read_error");
            log.warn("Skipping CMS cache read for {} because Redis is unavailable", redisKey, ex);
            return null;
        }
    }

    private void write(String cacheKey, String redisKey, Object value) {
        try {
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(value), properties.getCacheTtl());
            observabilityService.recordCacheWrite(cacheKey, "success");
        } catch (DataAccessException | IOException ex) {
            observabilityService.recordCacheWrite(cacheKey, "error");
            log.warn("Skipping CMS cache write for {}", redisKey, ex);
        }
    }

    private CacheInvalidationResult invalidateByKeys(String scope, List<String> rawKeys) {
        List<String> selectors = rawKeys.stream()
                .map(this::namespaced)
                .distinct()
                .toList();

        Long deleted = redisTemplate.delete(selectors);
        long deletedKeys = deleted != null ? deleted : 0;
        observabilityService.recordCacheInvalidation(scope, deletedKeys);
        return new CacheInvalidationResult(scope, keyPrefix(), selectors, deletedKeys);
    }

    private CacheInvalidationResult invalidateByPatterns(String scope, List<String> rawPatterns) {
        Set<String> keysToDelete = new LinkedHashSet<>();
        for (String rawPattern : rawPatterns) {
            keysToDelete.addAll(scanKeys(rawPattern));
        }

        Long deleted = keysToDelete.isEmpty() ? 0L : redisTemplate.delete(keysToDelete.stream().toList());
        long deletedKeys = deleted != null ? deleted : 0;
        observabilityService.recordCacheInvalidation(scope, deletedKeys);
        return new CacheInvalidationResult(scope, keyPrefix(), rawPatterns, deletedKeys);
    }

    private Set<String> scanKeys(String rawPattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(namespaced(rawPattern))
                .count(100)
                .build();

        Set<String> keys = new LinkedHashSet<>();
        redisTemplate.execute((RedisConnection connection) -> {
            try (var cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return keys;
    }

    private long safeDelete(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        try {
            Long deleted = redisTemplate.delete(keys);
            return deleted != null ? deleted : 0;
        } catch (DataAccessException ex) {
            log.warn("Failed to delete CMS cache keys {}", keys, ex);
            return 0;
        }
    }

    private boolean isCacheEnabled() {
        Duration ttl = properties.getCacheTtl();
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private String namespaced(String rawKey) {
        String prefix = keyPrefix();
        return StringUtils.hasText(prefix) ? prefix + ":" + rawKey : rawKey;
    }

    private String keyPrefix() {
        return StringUtils.hasText(properties.getCacheKeyPrefix())
                ? properties.getCacheKeyPrefix().trim().replaceAll(":+$", "")
                : "";
    }
}
