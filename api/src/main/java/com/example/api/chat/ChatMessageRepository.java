package com.example.api.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByConversation_IdOrderByCreatedAtAscIdAsc(UUID conversationId);

    @Query("""
            select message
            from ChatMessage message
            where message.conversation.id = :conversationId
              and (:after is null or message.createdAt > :after)
            order by message.createdAt asc, message.id asc
            """)
    List<ChatMessage> findConversationMessagesAfter(@Param("conversationId") UUID conversationId,
                                                    @Param("after") OffsetDateTime after);
}
