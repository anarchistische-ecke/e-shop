package com.example.api.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class RedisHealthController {
    private static final Logger log = LoggerFactory.getLogger(RedisHealthController.class);
    private final RedisConnectionFactory connectionFactory;

    public RedisHealthController(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @GetMapping("/redis")
    public ResponseEntity<Map<String, Object>> checkRedis() {
        Map<String, Object> body = new HashMap<>();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String ping = connection.ping();
            body.put("ok", true);
            body.put("ping", ping);
            body.put("description", "Redis connection successful");
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            log.error("Redis health check failed", ex);
            body.put("ok", false);
            body.put("error", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
