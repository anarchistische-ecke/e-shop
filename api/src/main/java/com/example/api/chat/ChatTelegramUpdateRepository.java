package com.example.api.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChatTelegramUpdateRepository extends JpaRepository<ChatTelegramUpdate, UUID> {
    boolean existsByUpdateId(Long updateId);
}
