package com.example.api.customer;

import com.example.customer.domain.Customer;
import com.example.customer.service.CustomerService;
import com.example.customer.service.EmailConfirmationService;
import com.example.api.notification.EmailService;
import com.example.common.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final EmailConfirmationService emailConfirmationService;
    private final EmailService emailService;

    @Autowired
    public CustomerController(CustomerService customerService,
                              EmailConfirmationService emailConfirmationService,
                              EmailService emailService) {
        this.customerService = customerService;
        this.emailConfirmationService = emailConfirmationService;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    public ResponseEntity<Customer> registerCustomer(@Valid @RequestBody CustomerRegistrationRequest request) {
        Address address = new Address(
                request.getStreet(),
                request.getCity(),
                request.getState(),
                request.getPostalCode(),
                request.getCountry()
        );
        Customer newCustomer = customerService.registerCustomer(
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                request.getPassword(),
                address
        );
        String email = newCustomer.getEmail();
        if (email != null && !email.isBlank()) {
            var confirmation = emailConfirmationService.createCode(email, newCustomer.getId());
            emailService.sendConfirmationCode(email, confirmation.getCode());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(newCustomer);
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerProfileResponse> getCurrentCustomer(@AuthenticationPrincipal Jwt jwt) {
        Customer customer = resolveCustomer(jwt);
        return ResponseEntity.ok(toProfileResponse(customer));
    }

    @PutMapping("/me")
    public ResponseEntity<CustomerProfileResponse> updateCurrentCustomer(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerProfileUpdateRequest request) {
        Customer customer = resolveCustomer(jwt);
        Customer updated = customerService.updateCustomerProfile(
                customer,
                request.email(),
                request.firstName(),
                request.lastName(),
                request.phone(),
                request.birthDate(),
                request.gender(),
                null
        );
        return ResponseEntity.ok(toProfileResponse(updated));
    }

    @PutMapping("/me/subscription")
    public ResponseEntity<CustomerProfileResponse> updateSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubscriptionUpdateRequest request) {
        Customer customer = resolveCustomer(jwt);
        Customer updated = customerService.updateCustomerProfile(
                customer,
                null,
                null,
                null,
                null,
                null,
                null,
                request.marketingOptIn()
        );
        return ResponseEntity.ok(toProfileResponse(updated));
    }

    @GetMapping(params = "email")
    public ResponseEntity<Customer> getCustomerByEmail(@RequestParam String email) {
        Customer customer = customerService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + email));
        return ResponseEntity.ok(customer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable UUID id) {
        Customer customer = customerService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        return ResponseEntity.ok(customer);
    }

    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @PostMapping
    public Customer createCustomer(@RequestBody Customer request) {
        return customerService.createCustomer(request);
    }

    @PostMapping("/verify/request")
    public ResponseEntity<Void> requestEmailConfirmation(@Valid @RequestBody EmailVerificationRequest request) {
        Customer customer = customerService.findByEmail(request.email())
                .orElse(null);
        UUID customerId = customer != null ? customer.getId() : null;
        var confirmation = emailConfirmationService.createCode(request.email(), customerId);
        emailService.sendConfirmationCode(request.email(), confirmation.getCode());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/verify/confirm")
    public ResponseEntity<Void> confirmEmail(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        boolean confirmed = emailConfirmationService.confirmCode(request.email(), request.code());
        if (!confirmed) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return ResponseEntity.noContent().build();
    }

    private Customer resolveCustomer(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Missing authentication token");
        }
        String email = jwt.getClaimAsString("email");
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        String displayName = jwt.getClaimAsString("name");
        if ((!StringUtils.hasText(firstName) || !StringUtils.hasText(lastName)) && StringUtils.hasText(displayName)) {
            String[] parts = displayName.trim().split("\\s+");
            if (!StringUtils.hasText(firstName) && parts.length > 0) {
                firstName = parts[0];
            }
            if (!StringUtils.hasText(lastName) && parts.length > 1) {
                lastName = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }
        }
        if (!StringUtils.hasText(email) && StringUtils.hasText(preferredUsername) && preferredUsername.contains("@")) {
            email = preferredUsername;
        }
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email is required to resolve customer profile");
        }
        return customerService.findOrCreateByEmail(email, firstName, lastName);
    }

    private CustomerProfileResponse toProfileResponse(Customer customer) {
        return new CustomerProfileResponse(
                customer.getId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPhone(),
                customer.getBirthDate(),
                customer.getGender(),
                customer.isMarketingOptIn(),
                customer.isEmailVerified()
        );
    }

    public record CustomerProfileUpdateRequest(
            @Email String email,
            String firstName,
            String lastName,
            @Pattern(regexp = "^\\+?\\d{11,15}$") String phone,
            LocalDate birthDate,
            String gender
    ) {}

    public record SubscriptionUpdateRequest(boolean marketingOptIn) {}

    public record CustomerProfileResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String phone,
            LocalDate birthDate,
            String gender,
            boolean marketingOptIn,
            boolean emailVerified
    ) {}

    public record EmailVerificationRequest(
            @Email @NotBlank String email
    ) {}

    public record EmailVerificationConfirmRequest(
            @Email @NotBlank String email,
            @NotBlank String code
    ) {}

    public static class CustomerRegistrationRequest {
        @NotBlank
        private String firstName;
        @NotBlank
        private String lastName;
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String password;
        @NotBlank
        private String street;
        @NotBlank
        private String city;
        private String state;
        @NotBlank
        private String postalCode;
        @NotBlank
        private String country;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }
}
