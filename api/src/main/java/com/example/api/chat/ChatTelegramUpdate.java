package com.example.api.chat;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "chat_telegram_update",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_telegram_update", columnNames = "update_id")
)
public class ChatTelegramUpdate extends BaseEntity {
    @Column(name = "update_id", nullable = false, unique = true)
    private Long updateId;

    @Column(name = "processed_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime processedAt = OffsetDateTime.now();

    public Long getUpdateId() {
        return updateId;
    }

    public void setUpdateId(Long updateId) {
        this.updateId = updateId;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
