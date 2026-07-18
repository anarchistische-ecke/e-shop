package com.example.api.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatDeliveryOutboxRepository extends JpaRepository<ChatDeliveryOutbox, UUID> {
    @Query("""
            select delivery
            from ChatDeliveryOutbox delivery
            join fetch delivery.conversation
            join fetch delivery.message
            where delivery.status in :statuses
              and delivery.nextAttemptAt <= :now
              and delivery.attemptCount < :maxAttempts
            order by delivery.createdAt asc
            """)
    List<ChatDeliveryOutbox> findDue(@Param("statuses") Collection<ChatDeliveryStatus> statuses,
                                     @Param("now") OffsetDateTime now,
                                     @Param("maxAttempts") int maxAttempts,
                                     Pageable pageable);
}
