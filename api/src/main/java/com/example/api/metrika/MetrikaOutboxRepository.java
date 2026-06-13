package com.example.api.metrika;

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
public interface MetrikaOutboxRepository extends JpaRepository<MetrikaOutbox, UUID> {
    boolean existsByEventKey(String eventKey);

    long countByStatus(MetrikaOutboxStatus status);

    @Query("""
            select event
            from MetrikaOutbox event
            where event.status in :statuses
              and event.nextAttemptAt <= :now
              and event.attemptCount < :maxAttempts
            order by event.createdAt asc
            """)
    List<MetrikaOutbox> findDue(@Param("statuses") Collection<MetrikaOutboxStatus> statuses,
                                @Param("now") OffsetDateTime now,
                                @Param("maxAttempts") int maxAttempts,
                                Pageable pageable);
}
