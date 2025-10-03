package ru.postel_yug.eshop.security.dto;

public record RegisterRequest(String email, String password, String name) {

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }


    public String getName() {
        return name;
    }
}
