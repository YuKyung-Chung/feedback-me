package com.jyk.feedbackme.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_credit_balance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCreditBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    private int balance;
    private int totalGranted;
    private int totalPurchased;
    private int totalUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public UserCreditBalance(AppUser user) {
        this.user = user;
        this.balance = 0;
        this.totalGranted = 0;
        this.totalPurchased = 0;
        this.totalUsed = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void grantFree(int amount) {
        this.balance += amount;
        this.totalGranted += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void purchase(int amount) {
        this.balance += amount;
        this.totalPurchased += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void use(int amount) {
        if (balance < amount) {
            throw new IllegalStateException("분석권이 부족합니다.");
        }
        this.balance -= amount;
        this.totalUsed += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void refund(int amount) {
        this.balance += amount;
        this.totalUsed = Math.max(0, this.totalUsed - amount);
        this.updatedAt = LocalDateTime.now();
    }
}
