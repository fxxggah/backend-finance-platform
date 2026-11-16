package com.finance.ai.entity;

import com.finance.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "ai_quota_guards") @Getter @Setter
public class AiQuotaGuard extends BaseEntity {
    @Id private Long id;
    private Instant blockedUntil;
    @Override public boolean equals(Object other) {
        return this == other || other instanceof AiQuotaGuard value && id != null && id.equals(value.getId());
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
