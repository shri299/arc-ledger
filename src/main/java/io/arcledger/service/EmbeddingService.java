package io.arcledger.service;

import java.util.List;

public interface EmbeddingService {
    double[] embed(String text);

    default List<double[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
