package com.jyk.feedbackme.dto;

import com.jyk.feedbackme.domain.AppUser;

public record AuthUserResponse(
        Long id,
        String email,
        String name
) {
    public static AuthUserResponse from(AppUser user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getName());
    }
}
