package com.example.admin.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "admin_user")
public class Admin extends BaseEntity {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MANAGER = "MANAGER";

    @NotBlank
    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @NotBlank
    @Column(name = "password", nullable = false)
    private String password;

    @NotBlank
    @Column(name = "role", nullable = false, length = 32)
    private String role = ROLE_ADMIN;

    public Admin() {
    }

    public Admin(String username, String password) {
        this.username = username;
        this.password = password;
        this.role = ROLE_ADMIN;
    }

    public Admin(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role != null && !role.isBlank() ? role : ROLE_ADMIN;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role != null && !role.isBlank() ? role : ROLE_ADMIN;
    }
}
