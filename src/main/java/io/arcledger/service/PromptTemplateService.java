package io.arcledger.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class PromptTemplateService {
    public String load(String name) {
        try {
            return new ClassPathResource("prompts/" + name + ".txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Missing prompt template: " + name, exception);
        }
    }
}
