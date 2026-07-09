package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.dto.AuthRequest;
import com.jyk.feedbackme.dto.AuthSessionResult;
import com.jyk.feedbackme.dto.AuthUserResponse;
import com.jyk.feedbackme.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        try {
            AuthSessionResult result = authService.register(request.email(), request.password(), request.name());
            return withSessionCookie(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            AuthSessionResult result = authService.login(request.email(), request.password());
            return withSessionCookie(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        authService.logout(request);
        ResponseCookie cookie = ResponseCookie.from(AuthService.SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", "Logged out."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        return authService.getCurrentUser(request)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(Map.of("user", AuthUserResponse.from(user))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다.")));
    }

    private ResponseEntity<?> withSessionCookie(AuthSessionResult result) {
        ResponseCookie cookie = ResponseCookie.from(AuthService.SESSION_COOKIE_NAME, result.token())
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(AuthService.SESSION_MAX_AGE_SECONDS))
                .build();

        AppUser user = result.user();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("user", AuthUserResponse.from(user)));
    }
}
