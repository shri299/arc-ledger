package io.arcledger.service;

public interface LanguageModelClient {
    String structuredCompletion(String systemPrompt, String userContent);
}
