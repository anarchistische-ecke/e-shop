package ru.postel_yug.eshop.security.entity;

import jakarta.persistence.*;

import javax.management.relation.Role;

@Entity
@Table(name = "users")
public class User {
    private Long id;
    private String username;
    private String password;
    private Role role;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ru.postel_yug.eshop.security.entity.Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}