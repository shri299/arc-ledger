package io.arcledger.service;

import io.arcledger.domain.FactIntent;
import io.arcledger.domain.KnowledgeKind;

public record ExtractedFact(String key, String value, KnowledgeKind knowledgeKind, FactIntent intent, String evidence) {}
