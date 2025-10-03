package ru.postel_yug.eshop.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.postel_yug.eshop.security.dto.ProfileDto;
import ru.postel_yug.eshop.security.dto.UpdateProfileRequest;
import ru.postel_yug.eshop.security.entity.User;
import ru.postel_yug.eshop.security.repository.UserRepository;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {
    @Autowired
    private UserRepository userRepo;

    // Получение профиля текущего пользователя
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();
        User user = userRepo.findByEmail(email).orElseThrow();
        // Не возвращаем пароль и другие внутренние поля - составляем DTO
        ProfileDto profile = new ProfileDto(user.getEmail(), user.getName());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest req, Authentication auth) {
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User user = userRepo.findByEmail(userDetails.getUsername()).orElseThrow();
        user.setName(req.getName());
        userRepo.save(user);
        return ResponseEntity.ok().build();
    }
}

