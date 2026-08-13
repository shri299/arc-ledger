package io.arcledger.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.arcledger.service.impl.OllamaEmbeddingService;
import io.arcledger.service.impl.OllamaLanguageModelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaClientsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    private HttpServer server;
    private URI baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = URI.create("http://localhost:" + server.getAddress().getPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void requestsNonStreamingStructuredGeneration() throws Exception {
        server.createContext("/api/generate", exchange -> respond(exchange,
            "{\"response\":\"{\\\"entities\\\":[]}\",\"done\":true}"));
        OllamaLanguageModelClient client = new OllamaLanguageModelClient(
            objectMapper, baseUrl, "gemma3:4b", "10m", Duration.ofSeconds(5));

        String result = client.structuredCompletion("extract facts", "John entered London.");

        assertThat(result).isEqualTo("{\"entities\":[]}");
        assertThat(requestBody.get().path("model").asText()).isEqualTo("gemma3:4b");
        assertThat(requestBody.get().path("format").asText()).isEqualTo("json");
        assertThat(requestBody.get().path("stream").asBoolean()).isFalse();
        assertThat(requestBody.get().path("options").path("temperature").asInt()).isZero();
    }

    @Test
    void batchesEmbeddingInputsInOneRequest() throws Exception {
        server.createContext("/api/embed", exchange -> respond(exchange,
            "{\"embeddings\":[[1.0,0.0],[0.0,1.0]]}"));
        OllamaEmbeddingService service = new OllamaEmbeddingService(
            objectMapper, baseUrl, "embeddinggemma", "10m", Duration.ofSeconds(5));

        List<double[]> vectors = service.embedAll(List.of("first question", "second question"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0, 0.0);
        assertThat(requestBody.get().path("input")).hasSize(2);
        assertThat(requestBody.get().path("model").asText()).isEqualTo("embeddinggemma");
    }

    private void respond(HttpExchange exchange, String response) throws IOException {
        requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
