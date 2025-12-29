package com.example.admin.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(
        name = "admin_activity",
        indexes = {
                @Index(name = "idx_admin_activity_username", columnList = "username"),
                @Index(name = "idx_admin_activity_created_at", columnList = "created_at")
        }
)
public class AdminActivity extends BaseEntity {

    @NotBlank
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @NotBlank
    @Column(name = "action", nullable = false, length = 255)
    private String action;

    @Column(name = "details", length = 1024)
    private String details;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
