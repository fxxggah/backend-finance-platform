package com.finance.ai.entity;

import com.finance.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "ai_global_attempts") @Getter @Setter
public class AiGlobalAttempt extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Instant reservedAt;
    @Override public boolean equals(Object other) {
        return this == other || other instanceof AiGlobalAttempt value && id != null && id.equals(value.getId());
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
