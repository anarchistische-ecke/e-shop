package com.example.api.marketing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/marketing/subscriptions")
public class MarketingSubscriptionController {

    private final MarketingSubscriptionService service;

    public MarketingSubscriptionController(MarketingSubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> subscribe(
            @Valid @RequestBody SubscriptionRequest request
    ) {
        service.requestSubscription(request.email(), request.consent(), request.source());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SubscriptionResponse("PENDING"));
    }

    @PostMapping("/confirm")
    public ResponseEntity<TokenResponse> confirm(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(new TokenResponse(service.confirm(request.token()).name()));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<TokenResponse> unsubscribe(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(new TokenResponse(service.unsubscribe(request.token()).name()));
    }

    public record SubscriptionRequest(
            @NotBlank @Email String email,
            @AssertTrue boolean consent,
            String source
    ) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record SubscriptionResponse(String status) {
    }

    public record TokenResponse(String status) {
    }
}
