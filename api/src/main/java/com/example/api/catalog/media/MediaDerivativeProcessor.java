package com.example.api.catalog.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class MediaDerivativeProcessor {
    private final ObjectMapper objectMapper;
    private final MediaUploadProperties properties;

    public MediaDerivativeProcessor(ObjectMapper objectMapper, MediaUploadProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ProcessedImage process(Path source, Path outputDirectory) {
        ProcessBuilder builder = new ProcessBuilder(
                properties.getNodeExecutable(),
                properties.getProcessorScript(),
                source.toString(),
                outputDirectory.toString(),
                String.valueOf(properties.getMaxPixels())
        );
        builder.environment().put("MEDIA_DERIVATIVES_CONFIG", properties.getDerivativeConfig());
        builder.environment().put("MALLOC_ARENA_MAX", "2");
        try {
            Files.createDirectories(outputDirectory);
            Process process = builder.start();
            CompletableFuture<byte[]> stderrFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return process.getErrorStream().readAllBytes();
                } catch (IOException error) {
                    throw new CompletionException(error);
                }
            });
            byte[] stdout = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            byte[] stderr = stderrFuture.join();
            if (exitCode != 0) {
                String message = new String(stderr, StandardCharsets.UTF_8).trim();
                throw new IllegalArgumentException(message.isBlank()
                        ? "Image optimization failed with exit code " + exitCode
                        : message);
            }
            return objectMapper.readValue(stdout, ProcessedImage.class);
        } catch (IOException error) {
            throw new IllegalStateException("Could not execute image processor", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Image processing was interrupted", error);
        } catch (CompletionException error) {
            throw new IllegalStateException("Could not read image processor output", error.getCause());
        }
    }

    public record ProcessedImage(
            String sourceFormat,
            String contentType,
            String extension,
            int width,
            int height,
            List<DerivativeFile> files
    ) {
    }

    public record DerivativeFile(
            int width,
            String format,
            String contentType,
            String path
    ) {
    }
}
