package ru.postel_yug.eshop.security.dto;

public record LoginRequest(String email, String password) {
    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}
