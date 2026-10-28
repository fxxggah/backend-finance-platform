package com.finance.account.entity;

import com.finance.common.entity.BaseEntity;
import com.finance.common.enums.AccountType;
import com.finance.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 60, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AccountType type;

    @Column(name = "initial_balance", precision = 19, scale = 2, nullable = false)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Column(length = 7)
    private String color;

    @Column(length = 40)
    private String icon;

    @Column(nullable = false)
    private boolean archived = false;

    public Account(User user, String name, AccountType type, BigDecimal initialBalance, String color, String icon) {
        this.user = user;
        this.name = name;
        this.type = type;
        this.initialBalance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
        this.color = color;
        this.icon = icon;
        this.archived = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Account other))
            return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
