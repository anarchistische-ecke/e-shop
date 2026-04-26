package com.example.api.content;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class CmsObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(CmsObservabilityService.class);

    private final MeterRegistry meterRegistry;
    private final DirectusContentProperties properties;

    public CmsObservabilityService(MeterRegistry meterRegistry, DirectusContentProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public <T> T recordDirectusRequest(
            String operation,
            ContentAccessMode accessMode,
            URI uri,
            Supplier<T> supplier
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String normalizedOperation = normalizeOperation(operation);
        String normalizedAccessMode = normalizeAccessMode(accessMode);
        String path = uri != null ? uri.getPath() : "unknown";

        try {
            T result = supplier.get();
            long durationNanos = stopDirectusTimer(sample, normalizedOperation, normalizedAccessMode, "success");
            logSlowRequestIfNeeded(normalizedOperation, normalizedAccessMode, path, durationNanos);
            return result;
        } catch (ContentNotFoundException ex) {
            long durationNanos = stopDirectusTimer(sample, normalizedOperation, normalizedAccessMode, "not_found");
            incrementDirectusError(normalizedOperation, normalizedAccessMode, "not_found");
            log.atInfo()
                    .addKeyValue("event", "cms_directus_content_not_found")
                    .addKeyValue("component", "cms")
                    .addKeyValue("operation", normalizedOperation)
                    .addKeyValue("access_mode", normalizedAccessMode)
                    .addKeyValue("path", path)
                    .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(durationNanos))
                    .log("Directus content lookup returned no matching record");
            throw ex;
        } catch (RestClientResponseException ex) {
            long durationNanos = stopDirectusTimer(sample, normalizedOperation, normalizedAccessMode, "http_error");
            incrementDirectusError(normalizedOperation, normalizedAccessMode, "http_" + ex.getStatusCode().value());
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "cms_directus_request_failed")
                    .addKeyValue("component", "cms")
                    .addKeyValue("operation", normalizedOperation)
                    .addKeyValue("access_mode", normalizedAccessMode)
                    .addKeyValue("path", path)
                    .addKeyValue("status", ex.getStatusCode().value())
                    .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(durationNanos))
                    .log("Directus request returned an HTTP error");
            throw ex;
        } catch (ResourceAccessException ex) {
            long durationNanos = stopDirectusTimer(sample, normalizedOperation, normalizedAccessMode, "network_error");
            incrementDirectusError(normalizedOperation, normalizedAccessMode, "network");
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "cms_directus_request_failed")
                    .addKeyValue("component", "cms")
                    .addKeyValue("operation", normalizedOperation)
                    .addKeyValue("access_mode", normalizedAccessMode)
                    .addKeyValue("path", path)
                    .addKeyValue("error_type", "network")
                    .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(durationNanos))
                    .log("Directus request failed due to connectivity or timeout issues");
            throw ex;
        } catch (RuntimeException ex) {
            long durationNanos = stopDirectusTimer(sample, normalizedOperation, normalizedAccessMode, "exception");
            incrementDirectusError(normalizedOperation, normalizedAccessMode, "exception");
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "cms_directus_request_failed")
                    .addKeyValue("component", "cms")
                    .addKeyValue("operation", normalizedOperation)
                    .addKeyValue("access_mode", normalizedAccessMode)
                    .addKeyValue("path", path)
                    .addKeyValue("error_type", "exception")
                    .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(durationNanos))
                    .log("Directus request failed with an unexpected exception");
            throw ex;
        }
    }

    public void recordCacheLookup(String rawCacheKey, String result) {
        Counter.builder("cms.cache.lookup")
                .description("CMS cache lookup results")
                .tags("cache", normalizeCache(rawCacheKey), "result", normalizeValue(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordCacheWrite(String rawCacheKey, String result) {
        Counter.builder("cms.cache.write")
                .description("CMS cache write results")
                .tags("cache", normalizeCache(rawCacheKey), "result", normalizeValue(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordCacheInvalidation(String scope, long deletedKeys) {
        DistributionSummary.builder("cms.cache.invalidation.deleted_keys")
                .description("Number of deleted CMS cache keys per invalidation operation")
                .tags("scope", normalizeValue(scope))
                .register(meterRegistry)
                .record(deletedKeys);

        log.atInfo()
                .addKeyValue("event", "cms_cache_invalidation")
                .addKeyValue("component", "cms")
                .addKeyValue("scope", normalizeValue(scope))
                .addKeyValue("deleted_keys", deletedKeys)
                .log("CMS cache invalidation completed");
    }

    private long stopDirectusTimer(Timer.Sample sample, String operation, String accessMode, String outcome) {
        Timer timer = Timer.builder("cms.directus.request")
                .description("Latency of Directus upstream requests used by the CMS facade")
                .tags("operation", operation, "access_mode", accessMode, "outcome", normalizeValue(outcome))
                .publishPercentileHistogram()
                .register(meterRegistry);
        return sample.stop(timer);
    }

    private void incrementDirectusError(String operation, String accessMode, String errorType) {
        Counter.builder("cms.directus.request.errors")
                .description("CMS Directus upstream request errors")
                .tags("operation", operation, "access_mode", accessMode, "error_type", normalizeValue(errorType))
                .register(meterRegistry)
                .increment();
    }

    private void logSlowRequestIfNeeded(String operation, String accessMode, String path, long durationNanos) {
        Duration threshold = properties.getSlowRequestThreshold();
        if (threshold == null || threshold.isNegative() || threshold.isZero()) {
            return;
        }

        Duration duration = Duration.ofNanos(durationNanos);
        if (duration.compareTo(threshold) < 0) {
            return;
        }

        log.atWarn()
                .addKeyValue("event", "cms_directus_request_slow")
                .addKeyValue("component", "cms")
                .addKeyValue("operation", operation)
                .addKeyValue("access_mode", accessMode)
                .addKeyValue("path", path)
                .addKeyValue("duration_ms", duration.toMillis())
                .addKeyValue("threshold_ms", threshold.toMillis())
                .log("Directus request exceeded the configured slow-request threshold");
    }

    private String normalizeOperation(String value) {
        return normalizeValue(value);
    }

    private String normalizeAccessMode(ContentAccessMode accessMode) {
        if (accessMode == null) {
            return "system";
        }
        return accessMode.isPreview() ? "preview" : "published";
    }

    private String normalizeCache(String rawCacheKey) {
        String normalized = normalizeValue(rawCacheKey);
        int separatorIndex = normalized.indexOf(':');
        return separatorIndex > 0 ? normalized.substring(0, separatorIndex) : normalized;
    }

    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }

        return value.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]+", "_");
    }
}
