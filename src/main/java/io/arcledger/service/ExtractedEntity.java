package io.arcledger.service;

import io.arcledger.domain.EntityType;
import java.util.List;

public record ExtractedEntity(String name, EntityType type, List<ExtractedFact> facts, List<String> relationships) {}
