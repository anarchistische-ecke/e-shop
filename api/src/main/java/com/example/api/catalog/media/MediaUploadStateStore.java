package com.example.api.catalog.media;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaUploadStateStore {
    private static final String RECORD_PREFIX = "catalogue:media:upload:";
    private static final String BATCH_PREFIX = "catalogue:media:batch:";
    private static final String ACTIVE_KEY = "catalogue:media:active";
    private static final String QUEUE_KEY = "catalogue:media:queue";
    private static final String EVENTS_KEY = "catalogue:media:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MediaUploadProperties properties;

    public MediaUploadStateStore(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            MediaUploadProperties properties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void create(MediaUploadRecord record) {
        save(record);
        String batchKey = batchKey(record.getBatchId());
        redis.opsForList().rightPush(batchKey, record.getId().toString());
        redis.expire(batchKey, properties.getRecordTtl());
    }

    public void save(MediaUploadRecord record) {
        record.setUpdatedAt(OffsetDateTime.now());
        try {
            redis.opsForValue().set(recordKey(record.getId()), objectMapper.writeValueAsString(record), properties.getRecordTtl());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not persist media upload state", error);
        }
        if (isActive(record.getStatus())) {
            redis.opsForSet().add(ACTIVE_KEY, record.getId().toString());
        } else {
            redis.opsForSet().remove(ACTIVE_KEY, record.getId().toString());
        }
        appendEvent(record);
    }

    public MediaUploadRecord require(UUID id) {
        MediaUploadRecord record = get(id);
        if (record == null) {
            throw new IllegalArgumentException("Media upload was not found: " + id);
        }
        return record;
    }

    public MediaUploadRecord get(UUID id) {
        String payload = redis.opsForValue().get(recordKey(id));
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, MediaUploadRecord.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not read media upload state", error);
        }
    }

    public List<MediaUploadRecord> getBatch(UUID batchId) {
        List<String> ids = redis.opsForList().range(batchKey(batchId), 0, -1);
        if (ids == null) {
            return List.of();
        }
        List<MediaUploadRecord> records = new ArrayList<>();
        for (String id : ids) {
            MediaUploadRecord record = get(UUID.fromString(id));
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    public List<MediaUploadRecord> listPending() {
        Set<String> ids = redis.opsForSet().members(ACTIVE_KEY);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(UUID::fromString)
                .map(this::get)
                .filter(record -> record != null)
                .sorted(Comparator.comparing(MediaUploadRecord::getCreatedAt).reversed())
                .toList();
    }

    public void enqueue(UUID uploadId) {
        redis.opsForStream().add(StreamRecords.mapBacked(Map.of(
                "uploadId", uploadId.toString(),
                "queuedAt", OffsetDateTime.now().toString()
        )).withStreamKey(QUEUE_KEY));
    }

    public QueuedUpload peekQueue() {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                StreamReadOptions.empty().count(1),
                StreamOffset.create(QUEUE_KEY, ReadOffset.from("0-0"))
        );
        if (records == null || records.isEmpty()) {
            return null;
        }
        MapRecord<String, Object, Object> record = records.getFirst();
        Object value = record.getValue().get("uploadId");
        if (value == null) {
            acknowledgeQueue(record.getId());
            return null;
        }
        return new QueuedUpload(record.getId(), UUID.fromString(String.valueOf(value)));
    }

    public void acknowledgeQueue(RecordId recordId) {
        redis.opsForStream().delete(QUEUE_KEY, recordId);
    }

    private void appendEvent(MediaUploadRecord record) {
        redis.opsForStream().add(StreamRecords.mapBacked(Map.of(
                "uploadId", record.getId().toString(),
                "batchId", record.getBatchId().toString(),
                "status", record.getStatus().name(),
                "updatedAt", record.getUpdatedAt().toString(),
                "attempt", String.valueOf(record.getAttemptCount())
        )).withStreamKey(EVENTS_KEY));
        redis.opsForStream().trim(EVENTS_KEY, 10_000);
    }

    private boolean isActive(MediaUploadModels.Status status) {
        return status != MediaUploadModels.Status.READY
                && status != MediaUploadModels.Status.ABORTED
                && status != MediaUploadModels.Status.EXPIRED;
    }

    private String recordKey(UUID id) {
        return RECORD_PREFIX + id;
    }

    private String batchKey(UUID id) {
        return BATCH_PREFIX + id;
    }

    public record QueuedUpload(RecordId recordId, UUID uploadId) {
    }
}
