package com.example.api.auth;

import com.example.admin.domain.Admin;
import com.example.admin.service.AdminService;
import com.example.admin.service.AdminActivityService;
import com.example.customer.domain.Customer;
import com.example.customer.service.CustomerService;
import com.example.api.config.JwtTokenUtil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminService adminService;
    private final AdminActivityService adminActivityService;
    private final CustomerService customerService;
    private final SocialAuthService socialAuthService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    public AuthController(AdminService adminService,
                          AdminActivityService adminActivityService,
                          CustomerService customerService,
                          SocialAuthService socialAuthService) {
        this.adminService = adminService;
        this.adminActivityService = adminActivityService;
        this.customerService = customerService;
        this.socialAuthService = socialAuthService;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<Map<String, String>> adminLogin(@RequestBody AdminLoginRequest request) {
        Optional<Admin> adminOpt = adminService.authenticate(request.getUsername(), request.getPassword());
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.singletonMap("error", "Invalid credentials"));
        }
        Admin admin = adminOpt.get();
        String token = JwtTokenUtil.generateToken(admin.getUsername(),
                "ROLE_ADMIN",
                24*60*60*1000,
                jwtSecret);
        adminActivityService.record(admin.getUsername(), "Admin login");
        return ResponseEntity.ok(Collections.singletonMap("token", token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> customerLogin(@RequestBody CustomerLoginRequest request) {
        Optional<Customer> custOpt = customerService.authenticate(request.getEmail(), request.getPassword());
        if (custOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Customer customer = custOpt.get();
        return ResponseEntity.ok(toAuthResponse(customer));
    }

    @PostMapping("/login/yandex")
    public ResponseEntity<AuthResponse> customerLoginWithYandex(@RequestBody YandexLoginRequest request) {
        try {
            SocialAuthService.SocialProfile profile = socialAuthService.fetchYandexProfile(
                    request.getAccessToken(),
                    request.getYandexId(),
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName()
            );
            Customer customer = customerService.findOrCreateByYandex(
                    profile.externalId(),
                    profile.email(),
                    profile.firstName(),
                    profile.lastName()
            );
            return ResponseEntity.ok(toAuthResponse(customer));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/login/vk")
    public ResponseEntity<AuthResponse> customerLoginWithVk(@RequestBody VkLoginRequest request) {
        try {
            SocialAuthService.SocialProfile profile = socialAuthService.fetchVkProfile(
                    request.getAccessToken(),
                    request.getVkUserId(),
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName()
            );
            Customer customer = customerService.findOrCreateByVk(
                    profile.externalId(),
                    profile.email(),
                    profile.firstName(),
                    profile.lastName()
            );
            return ResponseEntity.ok(toAuthResponse(customer));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs for login
    public static class AdminLoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    public static class CustomerLoginRequest {
        @Email @NotBlank
        private String email;
        @NotBlank
        private String password;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class YandexLoginRequest {
        private String accessToken;
        private String yandexId;
        private String email;
        private String firstName;
        private String lastName;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getYandexId() {
            return yandexId;
        }

        public void setYandexId(String yandexId) {
            this.yandexId = yandexId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

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
    }

    public static class VkLoginRequest {
        private String accessToken;
        private String vkUserId;
        private String email;
        private String firstName;
        private String lastName;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getVkUserId() {
            return vkUserId;
        }

        public void setVkUserId(String vkUserId) {
            this.vkUserId = vkUserId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

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
    }

    public static class AuthResponse {
        private String token;
        private CustomerProfile customer;

        public AuthResponse(String token, CustomerProfile customer) {
            this.token = token;
            this.customer = customer;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public CustomerProfile getCustomer() {
            return customer;
        }

        public void setCustomer(CustomerProfile customer) {
            this.customer = customer;
        }
    }

    public static class CustomerProfile {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
        private String yandexId;
        private String vkId;

        public CustomerProfile(UUID id, String email, String firstName, String lastName, String yandexId, String vkId) {
            this.id = id;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.yandexId = yandexId;
            this.vkId = vkId;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

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

        public String getYandexId() {
            return yandexId;
        }

        public void setYandexId(String yandexId) {
            this.yandexId = yandexId;
        }

        public String getVkId() {
            return vkId;
        }

        public void setVkId(String vkId) {
            this.vkId = vkId;
        }
    }

    private AuthResponse toAuthResponse(Customer customer) {
        String token = JwtTokenUtil.generateToken(customer.getEmail(),
                "ROLE_CUSTOMER",
                24*60*60*1000,
                jwtSecret);
        CustomerProfile profile = new CustomerProfile(
                customer.getId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getYandexId(),
                customer.getVkId()
        );
        return new AuthResponse(token, profile);
    }
}
