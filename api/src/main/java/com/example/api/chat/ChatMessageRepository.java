package com.example.api.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByConversation_IdOrderByCreatedAtAscIdAsc(UUID conversationId);

    List<ChatMessage> findByConversation_IdAndCreatedAtAfterOrderByCreatedAtAscIdAsc(
            UUID conversationId,
            OffsetDateTime after
    );
}
