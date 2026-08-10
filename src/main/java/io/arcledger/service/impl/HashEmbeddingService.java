package io.arcledger.service.impl;

import io.arcledger.service.EmbeddingService;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class HashEmbeddingService implements EmbeddingService {
    private static final int DIMENSIONS = 128;
    @Override public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.isBlank()) continue;
            int hash = fnv1a(token.getBytes(StandardCharsets.UTF_8));
            vector[Math.floorMod(hash, DIMENSIONS)] += ((hash & 1) == 0 ? 1.0 : -1.0);
        }
        double norm = 0.0; for (double value : vector) norm += value * value;
        norm = Math.sqrt(norm); if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        return vector;
    }
    private int fnv1a(byte[] bytes) { int hash = 0x811c9dc5; for (byte b : bytes) { hash ^= b & 0xff; hash *= 0x01000193; } return hash; }
}
