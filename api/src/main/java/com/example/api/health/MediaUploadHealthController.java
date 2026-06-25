package com.example.api.health;

import com.example.api.catalog.media.MediaObjectStorageService;
import com.example.api.catalog.media.MediaUploadProperties;
import com.example.api.catalog.media.MediaUploadFeatureFlag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/health/media")
public class MediaUploadHealthController {
    private final MediaUploadProperties properties;
    private final MediaObjectStorageService storage;
    private final MediaUploadFeatureFlag featureFlag;

    public MediaUploadHealthController(
            MediaUploadProperties properties,
            MediaObjectStorageService storage,
            MediaUploadFeatureFlag featureFlag
    ) {
        this.properties = properties;
        this.storage = storage;
        this.featureFlag = featureFlag;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadsEnabled", featureFlag.isEnabled());
        result.put("processorEnabled", properties.isProcessorEnabled());
        result.put("maxFileSize", properties.getMaxFileSize().toBytes());
        result.put("maxPixels", properties.getMaxPixels());
        try {
            requireRegularFile(properties.getProcessorScript(), "image processor");
            requireRegularFile(properties.getDerivativeConfig(), "derivative configuration");
            requireExecutable(properties.getNodeExecutable(), "Node.js runtime");
            storage.verifyBucketsAccessible();
            result.put("status", "UP");
            return ResponseEntity.ok(result);
        } catch (RuntimeException error) {
            result.put("status", "DOWN");
            result.put("error", error.getMessage());
            return ResponseEntity.status(503).body(result);
        }
    }

    private void requireRegularFile(String value, String label) {
        if (value == null || value.isBlank() || !Files.isRegularFile(Path.of(value))) {
            throw new IllegalStateException(label + " is missing");
        }
    }

    private void requireExecutable(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " is unavailable");
        }
        try {
            Process process = new ProcessBuilder(value, "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new IllegalStateException(label + " is unavailable");
            }
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(label + " is unavailable", error);
        }
    }
}
