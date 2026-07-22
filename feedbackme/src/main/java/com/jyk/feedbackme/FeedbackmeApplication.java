package com.jyk.feedbackme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/**
 * FeedbackMe 백엔드의 FeedbackmeApplication 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme 계층의 책임을 담당합니다.
 */
public class FeedbackmeApplication {

	public static void main(String[] args) {
		SpringApplication.run(FeedbackmeApplication.class, args);
	}

}
