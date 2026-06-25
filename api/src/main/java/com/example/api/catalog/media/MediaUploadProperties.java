package com.example.api.catalog.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "catalogue.media.upload")
public class MediaUploadProperties {
    private boolean enabled;
    private boolean processorEnabled = true;
    private DataSize maxFileSize = DataSize.ofMegabytes(100);
    private long maxPixels = 100_000_000L;
    private DataSize singlePutThreshold = DataSize.ofMegabytes(16);
    private DataSize multipartPartSize = DataSize.ofMegabytes(8);
    private Duration urlTtl = Duration.ofHours(1);
    private Duration sessionTtl = Duration.ofDays(7);
    private Duration recordTtl = Duration.ofDays(30);
    private Duration lockTtl = Duration.ofMinutes(5);
    private Duration lockRefreshInterval = Duration.ofSeconds(30);
    private long workerFixedDelayMs = 2000;
    private String pendingPrefix = "media-upload-pending";
    private String derivativeBucket = "";
    private String derivativePathPrefix = "media";
    private String nodeExecutable = "/usr/local/bin/node";
    private String processorScript = "/app/media/process-image.mjs";
    private String derivativeConfig = "/app/media/media-derivatives.json";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isProcessorEnabled() {
        return processorEnabled;
    }

    public void setProcessorEnabled(boolean processorEnabled) {
        this.processorEnabled = processorEnabled;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public long getMaxPixels() {
        return maxPixels;
    }

    public void setMaxPixels(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public DataSize getSinglePutThreshold() {
        return singlePutThreshold;
    }

    public void setSinglePutThreshold(DataSize singlePutThreshold) {
        this.singlePutThreshold = singlePutThreshold;
    }

    public DataSize getMultipartPartSize() {
        return multipartPartSize;
    }

    public void setMultipartPartSize(DataSize multipartPartSize) {
        this.multipartPartSize = multipartPartSize;
    }

    public Duration getUrlTtl() {
        return urlTtl;
    }

    public void setUrlTtl(Duration urlTtl) {
        this.urlTtl = urlTtl;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public Duration getRecordTtl() {
        return recordTtl;
    }

    public void setRecordTtl(Duration recordTtl) {
        this.recordTtl = recordTtl;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }

    public Duration getLockRefreshInterval() {
        return lockRefreshInterval;
    }

    public void setLockRefreshInterval(Duration lockRefreshInterval) {
        this.lockRefreshInterval = lockRefreshInterval;
    }

    public long getWorkerFixedDelayMs() {
        return workerFixedDelayMs;
    }

    public void setWorkerFixedDelayMs(long workerFixedDelayMs) {
        this.workerFixedDelayMs = workerFixedDelayMs;
    }

    public String getPendingPrefix() {
        return pendingPrefix;
    }

    public void setPendingPrefix(String pendingPrefix) {
        this.pendingPrefix = pendingPrefix;
    }

    public String getDerivativeBucket() {
        return derivativeBucket;
    }

    public void setDerivativeBucket(String derivativeBucket) {
        this.derivativeBucket = derivativeBucket;
    }

    public String getDerivativePathPrefix() {
        return derivativePathPrefix;
    }

    public void setDerivativePathPrefix(String derivativePathPrefix) {
        this.derivativePathPrefix = derivativePathPrefix;
    }

    public String getNodeExecutable() {
        return nodeExecutable;
    }

    public void setNodeExecutable(String nodeExecutable) {
        this.nodeExecutable = nodeExecutable;
    }

    public String getProcessorScript() {
        return processorScript;
    }

    public void setProcessorScript(String processorScript) {
        this.processorScript = processorScript;
    }

    public String getDerivativeConfig() {
        return derivativeConfig;
    }

    public void setDerivativeConfig(String derivativeConfig) {
        this.derivativeConfig = derivativeConfig;
    }
}
