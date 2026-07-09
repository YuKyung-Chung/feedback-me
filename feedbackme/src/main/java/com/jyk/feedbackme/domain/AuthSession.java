package com.jyk.feedbackme.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;

    @Builder
    public AuthSession(AppUser user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
        this.lastUsedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }

    public void touch() {
        this.lastUsedAt = LocalDateTime.now();
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}
