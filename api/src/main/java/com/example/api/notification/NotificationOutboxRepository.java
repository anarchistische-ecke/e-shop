package com.example.api.notification;

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
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {
    boolean existsByEventKey(String eventKey);

    long countByStatus(NotificationOutboxStatus status);

    @Query("""
            select notification
            from NotificationOutbox notification
            where notification.status in :statuses
              and notification.nextAttemptAt <= :now
              and notification.attemptCount < :maxAttempts
            order by notification.createdAt asc
            """)
    List<NotificationOutbox> findDue(@Param("statuses") Collection<NotificationOutboxStatus> statuses,
                                     @Param("now") OffsetDateTime now,
                                     @Param("maxAttempts") int maxAttempts,
                                     Pageable pageable);
}
