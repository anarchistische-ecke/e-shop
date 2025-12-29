package com.example.admin.repository;

import com.example.admin.domain.AdminActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminActivityRepository extends JpaRepository<AdminActivity, UUID> {
}
