package io.arcledger.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Service
@ConditionalOnProperty(name = "arcledger.embedding.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaEmbeddingService implements EmbeddingService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String model;
    private final String keepAlive;
    private final Duration requestTimeout;

    public OllamaEmbeddingService(ObjectMapper objectMapper,
        @Value("${arcledger.ollama.base-url:http://localhost:11434}") URI baseUrl,
        @Value("${arcledger.embedding.model:embeddinggemma}") String model,
        @Value("${arcledger.ollama.keep-alive:10m}") String keepAlive,
        @Value("${arcledger.ollama.request-timeout:120s}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.endpoint = baseUrl.resolve("/api/embed");
        this.model = model;
        this.keepAlive = keepAlive;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public double[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<double[]> embedAll(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", texts);
            body.put("truncate", true);
            body.put("keep_alive", keepAlive);

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Ollama embedding failed with HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode embeddings = objectMapper.readTree(response.body()).path("embeddings");
            List<double[]> result = StreamSupport.stream(embeddings.spliterator(), false)
                .map(node -> objectMapper.convertValue(node, double[].class)).toList();
            if (result.size() != texts.size()) {
                throw new IllegalStateException("Ollama returned " + result.size() + " embeddings for " + texts.size() + " inputs");
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama embedding was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Could not call Ollama at " + endpoint, exception);
        }
    }
}
