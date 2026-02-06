package com.example.admin.service;

import com.example.admin.repository.AdminRepository;
import com.example.admin.domain.Admin;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.username:admin}")
    private String defaultUsername;

    @Value("${admin.default.password:admin123}")
    private String defaultPassword;

    @Autowired
    public AdminService(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        if (adminRepository.count() == 0) {
            Admin admin = new Admin(defaultUsername, passwordEncoder.encode(defaultPassword), Admin.ROLE_ADMIN);
            adminRepository.save(admin);
        }
    }

    public Optional<Admin> authenticate(String username, String password) {
        return adminRepository.findByUsername(username)
                .filter(admin -> passwordEncoder.matches(password, admin.getPassword()));
    }

    public Optional<Admin> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return adminRepository.findByUsername(username);
    }
}
