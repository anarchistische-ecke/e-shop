package ru.postel_yug.eshop.security.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.security.entity.User;
import ru.postel_yug.eshop.security.repository.RoleRepository;
import ru.postel_yug.eshop.security.repository.UserRepository;

import javax.management.relation.Role;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private RoleRepository roleRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public User registerUser(String email, String rawPassword, String name) {
        if(userRepo.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email уже зарегистрирован");
        }
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPassword(passwordEncoder.encode(rawPassword));
        Role userRole = roleRepo.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Базовая роль не найдена"));
        user.getRoles().add(userRole);
        User saved = userRepo.save(user);
        return saved;
    }
}

