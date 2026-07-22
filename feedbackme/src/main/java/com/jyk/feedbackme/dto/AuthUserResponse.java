package com.jyk.feedbackme.dto;

import com.jyk.feedbackme.domain.AppUser;
/**
 * FeedbackMe 백엔드의 AuthUserResponse 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.dto 계층의 책임을 담당합니다.
 */

public record AuthUserResponse(
        Long id,
        String email,
        String name
) {
    public static AuthUserResponse from(AppUser user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getName());
    }
}
