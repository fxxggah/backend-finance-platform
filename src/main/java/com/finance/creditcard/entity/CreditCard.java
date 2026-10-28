package com.finance.creditcard.entity;

import com.finance.common.entity.BaseEntity;
import com.finance.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "credit_cards")
@Getter
@Setter
@NoArgsConstructor
public class CreditCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 60, nullable = false)
    private String name;

    @Column(name = "credit_limit", precision = 19, scale = 2, nullable = false)
    private BigDecimal creditLimit;

    @Column(name = "closing_day", nullable = false)
    private Integer closingDay;

    @Column(name = "due_day", nullable = false)
    private Integer dueDay;

    @Column(length = 7)
    private String color;

    @Column(length = 40)
    private String icon;

    @Column(nullable = false)
    private boolean archived = false;

    public CreditCard(User user, String name, BigDecimal creditLimit, Integer closingDay, Integer dueDay, String color,
            String icon) {
        this.user = user;
        this.name = name;
        this.creditLimit = creditLimit;
        this.closingDay = closingDay;
        this.dueDay = dueDay;
        this.color = color;
        this.icon = icon;
        this.archived = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CreditCard other))
            return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
