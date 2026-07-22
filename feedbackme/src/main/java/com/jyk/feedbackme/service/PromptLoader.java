package com.jyk.feedbackme.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PromptLoader {

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
