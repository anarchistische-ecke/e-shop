package com.example.api.catalog.media;

import com.example.admin.service.AdminActivityService;
import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.api.catalog.DirectusStorefrontOpsRolePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/directus/catalogue/media/uploads")
public class DirectusMediaUploadController {
    private final MediaUploadService uploadService;
    private final DirectusBridgeSecurity bridgeSecurity;
    private final DirectusStorefrontOpsRolePolicy rolePolicy;
    private final AdminActivityService activityService;
    private final ObjectMapper objectMapper;

    public DirectusMediaUploadController(
            MediaUploadService uploadService,
            DirectusBridgeSecurity bridgeSecurity,
            DirectusStorefrontOpsRolePolicy rolePolicy,
            AdminActivityService activityService,
            ObjectMapper objectMapper
    ) {
        this.uploadService = uploadService;
        this.bridgeSecurity = bridgeSecurity;
        this.rolePolicy = rolePolicy;
        this.activityService = activityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<MediaUploadModels.CreateBatchResponse> create(
            @Valid @RequestBody MediaUploadModels.CreateBatchRequest body,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        MediaUploadModels.CreateBatchResponse response = uploadService.createBatch(body, principal);
        audit(principal, "catalogue.media.upload.create", Map.of(
                "batchId", response.batchId(),
                "targetType", body.targetType(),
                "entityId", body.entityId(),
                "count", body.files().size()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{uploadId}/parts")
    public ResponseEntity<MediaUploadModels.SignPartsResponse> signParts(
            @PathVariable UUID uploadId,
            @Valid @RequestBody MediaUploadModels.SignPartsRequest body,
            HttpServletRequest request
    ) {
        authorize(request);
        return ResponseEntity.ok(uploadService.signParts(uploadId, body));
    }

    @PostMapping("/{uploadId}/url")
    public ResponseEntity<MediaUploadModels.RenewSingleResponse> renewSingle(
            @PathVariable UUID uploadId,
            HttpServletRequest request
    ) {
        authorize(request);
        return ResponseEntity.ok(uploadService.renewSingle(uploadId));
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<MediaUploadModels.UploadItemResponse> complete(
            @PathVariable UUID uploadId,
            @Valid @RequestBody(required = false) MediaUploadModels.CompleteUploadRequest body,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        MediaUploadModels.UploadItemResponse response = uploadService.complete(uploadId, body);
        audit(principal, "catalogue.media.upload.complete", Map.of(
                "uploadId", uploadId,
                "batchId", response.batchId()
        ));
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/{uploadId}/retry")
    public ResponseEntity<MediaUploadModels.UploadItemResponse> retry(
            @PathVariable UUID uploadId,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        MediaUploadModels.UploadItemResponse response = uploadService.retry(uploadId);
        audit(principal, "catalogue.media.upload.retry", Map.of("uploadId", uploadId));
        return ResponseEntity.accepted().body(response);
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<MediaUploadModels.UploadItemResponse> abort(
            @PathVariable UUID uploadId,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        MediaUploadModels.UploadItemResponse response = uploadService.abort(uploadId);
        audit(principal, "catalogue.media.upload.abort", Map.of("uploadId", uploadId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<MediaUploadModels.UploadBatchStatus> batch(
            @PathVariable UUID batchId,
            HttpServletRequest request
    ) {
        authorize(request);
        return ResponseEntity.ok(uploadService.batchStatus(batchId));
    }

    @GetMapping
    public ResponseEntity<List<MediaUploadModels.UploadItemResponse>> pending(
            @RequestParam(required = false) MediaUploadModels.TargetType targetType,
            @RequestParam(required = false) UUID entityId,
            HttpServletRequest request
    ) {
        authorize(request);
        return ResponseEntity.ok(uploadService.pending(targetType, entityId));
    }

    @GetMapping("/feature")
    public ResponseEntity<MediaUploadModels.FeatureFlagResponse> feature(HttpServletRequest request) {
        authorize(request);
        return ResponseEntity.ok(new MediaUploadModels.FeatureFlagResponse(uploadService.uploadsEnabled()));
    }

    @PutMapping("/feature")
    public ResponseEntity<MediaUploadModels.FeatureFlagResponse> setFeature(
            @RequestBody MediaUploadModels.FeatureFlagRequest body,
            HttpServletRequest request
    ) {
        bridgeSecurity.authorize(request);
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = bridgeSecurity.principal(request);
        rolePolicy.requireAdmin(principal, "media upload feature flag");
        boolean enabled = uploadService.setUploadsEnabled(body.enabled());
        audit(principal, "catalogue.media.upload.feature", Map.of("enabled", enabled));
        return ResponseEntity.ok(new MediaUploadModels.FeatureFlagResponse(enabled));
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal authorize(HttpServletRequest request) {
        bridgeSecurity.authorize(request);
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = bridgeSecurity.principal(request);
        rolePolicy.requireCatalogue(principal);
        return principal;
    }

    private void audit(
            DirectusBridgeSecurity.DirectusBridgePrincipal principal,
            String action,
            Map<String, Object> details
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("directusUserId", principal.userId());
        payload.put("directusEmail", principal.email());
        payload.putAll(details);
        try {
            activityService.record(principal.actor(), action, objectMapper.writeValueAsString(payload));
        } catch (Exception error) {
            activityService.record(principal.actor(), action, String.valueOf(payload));
        }
    }
}
