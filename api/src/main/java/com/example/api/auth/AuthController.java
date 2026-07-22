package com.example.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final MagicLinkService magicLinkService;

    public AuthController(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;
    }

    @PostMapping("/magic-link")
    public ResponseEntity<?> requestMagicLink(@Valid @RequestBody MagicLinkRequest request) {
        MagicLinkService.MagicLinkResult result =
                magicLinkService.requestMagicLink(request.email(), request.redirectUri());

        return switch (result.status()) {
            case ACCEPTED -> ResponseEntity.accepted().build();
            case VALIDATION_ERROR -> ResponseEntity.badRequest().body(Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", result.message()
            ));
            case RATE_LIMITED -> {
                HttpHeaders headers = new HttpHeaders();
                long retryAfterSeconds = Math.max(1, Duration.between(Instant.now(), result.retryAt()).toSeconds());
                headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
                yield ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .headers(headers)
                        .body(Map.of(
                                "code", "RATE_LIMITED",
                                "message", result.message(),
                                "retryAfterSeconds", retryAfterSeconds
                        ));
            }
            case UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", "MAGIC_LINK_UNAVAILABLE",
                    "message", result.message()
            ));
        };
    }

    public record MagicLinkRequest(
            @NotBlank @Email String email,
            @NotBlank String redirectUri
    ) {
    }
}
