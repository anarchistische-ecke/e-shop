package com.example.api.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectusContentCacheServiceTest {

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CmsObservabilityService observabilityService;

    private final Map<String, String> store = new LinkedHashMap<>();

    private DirectusContentCacheService service;

    @BeforeEach
    void setUp() {
        DirectusContentProperties properties = new DirectusContentProperties();
        properties.setCacheTtl(Duration.ofMinutes(5));
        properties.setCacheStaleTtl(Duration.ofHours(1));
        properties.setCacheKeyPrefix("cms:content");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(redisTemplate.delete(anyList())).thenAnswer(invocation -> {
            long deleted = 0;
            @SuppressWarnings("unchecked")
            Iterable<String> keys = (Iterable<String>) invocation.getArgument(0);
            for (String key : keys) {
                if (store.remove(key) != null) {
                    deleted += 1;
                }
            }
            return deleted;
        });

        service = new DirectusContentCacheService(redisTemplate, new ObjectMapper(), properties, observabilityService);
    }

    @Test
    void getOrLoad_writesFreshAndStaleCopiesOnMiss() {
        Map<String, String> payload = service.getOrLoad("page:delivery", STRING_MAP_TYPE, () -> Map.of("title", "Delivery"));

        assertThat(payload).containsEntry("title", "Delivery");
        assertThat(store).containsKeys(
                "cms:content:page:delivery",
                "cms:content:page:delivery:stale"
        );
        verify(observabilityService).recordCacheLookup("page:delivery", "miss");
        verify(observabilityService).recordCacheWrite("page:delivery", "success");
    }

    @Test
    void getOrLoad_servesStaleCopyWhenLoaderFails() throws Exception {
        store.put(
                "cms:content:page:delivery:stale",
                new ObjectMapper().writeValueAsString(Map.of("title", "Cached delivery"))
        );

        Map<String, String> payload = service.getOrLoad(
                "page:delivery",
                STRING_MAP_TYPE,
                () -> {
                    throw new IllegalStateException("Directus unavailable");
                }
        );

        assertThat(payload).containsEntry("title", "Cached delivery");
        verify(observabilityService).recordCacheLookup("page:delivery", "stale_hit");
    }

    @Test
    void getOrLoad_rethrowsWhenFreshAndStaleCopiesAreMissing() {
        assertThatThrownBy(() -> service.getOrLoad(
                "page:delivery",
                STRING_MAP_TYPE,
                () -> {
                    throw new IllegalStateException("Directus unavailable");
                }
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Directus unavailable");

        verify(observabilityService).recordCacheLookup("page:delivery", "stale_miss");
    }

    @Test
    void invalidatePage_deletesFreshAndStaleKeys() {
        store.put("cms:content:page:delivery", "{\"title\":\"Delivery\"}");
        store.put("cms:content:page:delivery:stale", "{\"title\":\"Cached delivery\"}");

        DirectusContentCacheService.CacheInvalidationResult result = service.invalidatePage("delivery");

        assertThat(store).doesNotContainKeys(
                "cms:content:page:delivery",
                "cms:content:page:delivery:stale"
        );
        assertThat(result.deletedKeys()).isEqualTo(2);
        assertThat(result.selectors()).containsExactlyInAnyOrder(
                "cms:content:page:delivery",
                "cms:content:page:delivery:stale"
        );
        verify(observabilityService).recordCacheInvalidation("page", 2);
    }
}
