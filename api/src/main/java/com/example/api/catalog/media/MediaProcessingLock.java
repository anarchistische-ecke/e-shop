package com.example.api.catalog.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class MediaProcessingLock {
    private static final Logger log = LoggerFactory.getLogger(MediaProcessingLock.class);
    private static final String LOCK_KEY = "catalogue:media:processor-lock";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );
    private static final DefaultRedisScript<Long> REFRESH_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final MediaUploadProperties properties;

    public MediaProcessingLock(StringRedisTemplate redis, MediaUploadProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Lease tryAcquire() {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, token, properties.getLockTtl());
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }
        return new Lease(token);
    }

    public final class Lease implements AutoCloseable {
        private final String token;
        private final ScheduledExecutorService refresher;

        private Lease(String token) {
            this.token = token;
            this.refresher = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "media-processing-lock-refresh");
                thread.setDaemon(true);
                return thread;
            });
            long interval = Math.max(1000, properties.getLockRefreshInterval().toMillis());
            refresher.scheduleAtFixedRate(this::refresh, interval, interval, TimeUnit.MILLISECONDS);
        }

        private void refresh() {
            try {
                redis.execute(
                        REFRESH_SCRIPT,
                        List.of(LOCK_KEY),
                        token,
                        String.valueOf(properties.getLockTtl().toMillis())
                );
            } catch (RuntimeException error) {
                log.warn("Could not refresh the media processing lock", error);
            }
        }

        @Override
        public void close() {
            refresher.shutdownNow();
            redis.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), token);
        }
    }
}
