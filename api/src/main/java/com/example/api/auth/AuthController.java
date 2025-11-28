package com.example.api.auth;

import com.example.admin.domain.Admin;
import com.example.admin.service.AdminService;
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

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminService adminService;
    private final CustomerService customerService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    public AuthController(AdminService adminService, CustomerService customerService) {
        this.adminService = adminService;
        this.customerService = customerService;
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
        return ResponseEntity.ok(Collections.singletonMap("token", token));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> customerLogin(@RequestBody CustomerLoginRequest request) {
        Optional<Customer> custOpt = customerService.authenticate(request.getEmail(), request.getPassword());
        if (custOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.singletonMap("error", "Invalid credentials"));
        }
        Customer customer = custOpt.get();
        String token = JwtTokenUtil.generateToken(customer.getEmail(),
                "ROLE_CUSTOMER",
                24*60*60*1000,
                jwtSecret);
        return ResponseEntity.ok(Collections.singletonMap("token", token));
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
}