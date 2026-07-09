package com.jyk.feedbackme.dto;

import com.jyk.feedbackme.domain.AppUser;

public record AuthSessionResult(
        AppUser user,
        String token
) {
}
