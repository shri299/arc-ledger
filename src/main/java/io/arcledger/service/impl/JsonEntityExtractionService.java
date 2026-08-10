package io.arcledger.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.service.*;
import org.springframework.stereotype.Service;

@Service
public class JsonEntityExtractionService implements EntityExtractionService {
    private final LanguageModelClient client;
    private final PromptTemplateService prompts;
    private final ObjectMapper objectMapper;

    public JsonEntityExtractionService(LanguageModelClient client, PromptTemplateService prompts, ObjectMapper objectMapper) {
        this.client = client; this.prompts = prompts; this.objectMapper = objectMapper;
    }
    @Override
    public ExtractionResult extract(String sceneText) {
        String json = client.structuredCompletion(prompts.load("entity-extraction"), sceneText);
        try { return objectMapper.readValue(json, ExtractionResult.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("LLM returned invalid extraction JSON", exception); }
    }
}
