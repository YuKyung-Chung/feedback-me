package com.jyk.feedbackme.dto;

import com.jyk.feedbackme.domain.AppUser;
/**
 * FeedbackMe 백엔드의 AuthSessionResult 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.dto 계층의 책임을 담당합니다.
 */

public record AuthSessionResult(
        AppUser user,
        String token
) {
}
