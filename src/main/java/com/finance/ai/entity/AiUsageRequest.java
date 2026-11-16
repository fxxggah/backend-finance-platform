package com.finance.ai.entity;

import com.finance.common.entity.BaseEntity;
import com.finance.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "ai_usage_requests") @Getter @Setter
public class AiUsageRequest extends BaseEntity {
    public enum Status { PROCESSING, SUCCEEDED, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 64) private String keyHash;
    @Column(nullable = false, length = 64) private String audioHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false) private Instant reservedAt;
    @Column(nullable = false) private Instant leaseUntil;
    private LocalDate successDay;
    @Override public boolean equals(Object other) {
        return this == other || other instanceof AiUsageRequest value && id != null && id.equals(value.getId());
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
