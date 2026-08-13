package io.arcledger.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.service.LanguageModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "arcledger.llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLanguageModelClient implements LanguageModelClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String model;
    private final String keepAlive;
    private final Duration requestTimeout;

    public OllamaLanguageModelClient(ObjectMapper objectMapper,
        @Value("${arcledger.ollama.base-url:http://localhost:11434}") URI baseUrl,
        @Value("${arcledger.llm.model:gemma3:4b}") String model,
        @Value("${arcledger.ollama.keep-alive:10m}") String keepAlive,
        @Value("${arcledger.ollama.request-timeout:120s}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.endpoint = baseUrl.resolve("/api/generate");
        this.model = model;
        this.keepAlive = keepAlive;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String structuredCompletion(String systemPrompt, String userContent) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("system", systemPrompt);
            body.put("prompt", userContent);
            body.put("format", "json");
            body.put("stream", false);
            body.put("keep_alive", keepAlive);
            body.put("options", Map.of("temperature", 0));

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Ollama generation failed with HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String generated = root.path("response").asText();
            if (generated.isBlank()) throw new IllegalStateException("Ollama returned an empty structured response");
            return generated;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama generation was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Could not call Ollama at " + endpoint, exception);
        }
    }
}
