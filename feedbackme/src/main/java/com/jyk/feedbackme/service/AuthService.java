package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.AuthSession;
import com.jyk.feedbackme.dto.AuthSessionResult;
import com.jyk.feedbackme.repository.AppUserRepository;
import com.jyk.feedbackme.repository.AuthSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
/**
 * FeedbackMe 백엔드의 AuthService 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.service 계층의 책임을 담당합니다.
 */
public class AuthService {

    public static final String SESSION_COOKIE_NAME = "feedbackme_session";
    public static final long SESSION_MAX_AGE_SECONDS = 60L * 60 * 24 * 14;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final CreditService creditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppUserRepository appUserRepository, AuthSessionRepository authSessionRepository, CreditService creditService) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.creditService = creditService;
    }

    @Transactional
    public AuthSessionResult register(String email, String password, String name) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = normalizeName(name, normalizedEmail);
        validateEmail(normalizedEmail);
        validatePassword(password);

        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        AppUser user = AppUser.builder()
                .email(normalizedEmail)
                .name(normalizedName)
                .passwordHash(passwordEncoder.encode(password))
                .build();

        appUserRepository.save(user);
        creditService.grantSignupCredits(user);
        return createSession(user);
    }

    @Transactional
    public AuthSessionResult login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호를 확인해 주세요."));

        if (!passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호를 확인해 주세요.");
        }

        creditService.grantSignupCreditsIfMissing(user);
        return createSession(user);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> getCurrentUser(HttpServletRequest request) {
        String token = extractToken(request);
        if (token.isBlank()) {
            return Optional.empty();
        }

        return authSessionRepository.findByTokenHashAndRevokedAtIsNull(hashToken(token))
                .filter(AuthSession::isActive)
                .map(AuthSession::getUser);
    }

    public AppUser requireUser(HttpServletRequest request) {
        return getCurrentUser(request)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다."));
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token.isBlank()) {
            return;
        }

        authSessionRepository.findByTokenHashAndRevokedAtIsNull(hashToken(token))
                .ifPresent(AuthSession::revoke);
    }

    private AuthSessionResult createSession(AppUser user) {
        String token = generateToken();
        AuthSession session = AuthSession.builder()
                .user(user)
                .tokenHash(hashToken(token))
                .expiresAt(LocalDateTime.now().plusSeconds(SESSION_MAX_AGE_SECONDS))
                .build();
        authSessionRepository.save(session);
        return new AuthSessionResult(user, token);
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }

        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue() == null ? "" : cookie.getValue();
            }
        }

        return "";
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash session token.", e);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeName(String name, String email) {
        String trimmedName = name == null ? "" : name.trim();
        if (!trimmedName.isBlank()) {
            return trimmedName;
        }
        return email.contains("@") ? email.substring(0, email.indexOf("@")) : "사용자";
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일을 입력해 주세요.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");
        }
    }
}
