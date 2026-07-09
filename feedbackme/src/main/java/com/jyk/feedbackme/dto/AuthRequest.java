package com.jyk.feedbackme.dto;

public record AuthRequest(
        String email,
        String password,
        String name
) {
}
