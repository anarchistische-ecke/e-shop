package com.example.api.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatTelegramMessageMapRepository extends JpaRepository<ChatTelegramMessageMap, UUID> {
    Optional<ChatTelegramMessageMap> findByTelegramChatIdAndTelegramMessageId(Long telegramChatId, Long telegramMessageId);
}
