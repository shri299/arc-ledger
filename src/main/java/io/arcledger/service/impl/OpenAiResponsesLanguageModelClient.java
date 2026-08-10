package io.arcledger.service.impl;

import com.fasterxml.jackson.databind.*;
import io.arcledger.service.LanguageModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
@ConditionalOnProperty(name = "arcledger.llm.provider", havingValue = "openai")
public class OpenAiResponsesLanguageModelClient implements LanguageModelClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;

    public OpenAiResponsesLanguageModelClient(ObjectMapper objectMapper,
        @Value("${arcledger.llm.endpoint}") URI endpoint,
        @Value("${arcledger.llm.api-key}") String apiKey,
        @Value("${arcledger.llm.model}") String model) {
        this.objectMapper = objectMapper; this.endpoint = endpoint; this.apiKey = apiKey; this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY is required when arcledger.llm.provider=openai");
    }

    @Override
    public String structuredCompletion(String systemPrompt, String userContent) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("store", false);
            body.put("input", List.of(
                Map.of("role", "developer", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)));
            body.put("text", Map.of("format", Map.of("type", "json_object")));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2)
                throw new IllegalStateException("LLM request failed with HTTP " + response.statusCode() + ": " + response.body());
            JsonNode root = objectMapper.readTree(response.body());
            for (JsonNode output : root.path("output")) for (JsonNode content : output.path("content"))
                if (content.hasNonNull("text")) return content.get("text").asText();
            throw new IllegalStateException("LLM response did not contain output text");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("LLM request interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("LLM request failed", exception);
        }
    }
}
