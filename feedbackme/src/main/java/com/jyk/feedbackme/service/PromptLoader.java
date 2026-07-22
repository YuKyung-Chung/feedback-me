package com.jyk.feedbackme.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 버전이 지정된 classpath 프롬프트를 읽고 변수를 치환합니다. */
@Component
/**
 * FeedbackMe 백엔드의 PromptLoader 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.service 계층의 책임을 담당합니다.
 */
public class PromptLoader {

    /** prompts/{promptName}/{version}.md 파일을 읽어 {{변수명}}을 치환합니다. */
    public String load(String promptName, String version, Map<String, String> variables) {
        String path = "prompts/" + promptName + "/" + version + ".md";
        try {
            String template = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            String rendered = template;
            for (Map.Entry<String, String> variable : variables.entrySet()) {
                rendered = rendered.replace("{{" + variable.getKey() + "}}", variable.getValue());
            }
            return rendered;
        } catch (IOException exception) {
            throw new IllegalStateException("Prompt resource is missing or unreadable: " + path, exception);
        }
    }
}
