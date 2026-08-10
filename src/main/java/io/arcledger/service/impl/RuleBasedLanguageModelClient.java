package io.arcledger.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.*;
import io.arcledger.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.*;

@Component
@ConditionalOnProperty(name = "arcledger.llm.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedLanguageModelClient implements LanguageModelClient {
    private final ObjectMapper objectMapper;

    public RuleBasedLanguageModelClient(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    public String structuredCompletion(String systemPrompt, String text) {
        Map<String, List<ExtractedFact>> facts = new LinkedHashMap<>();
        BiConsumer<String, ExtractedFact> add = (name, fact) -> facts.computeIfAbsent(name, ignored -> new ArrayList<>()).add(fact);

        match(text, "(?i)\\b([A-Z][a-z]+)\\s+(?:has|had)\\s+([a-z]+)\\s+hair", m ->
            add.accept(cap(m.group(1)), fact("hairColor", m.group(2), FactIntent.ASSERTION, m.group())));
        match(text, "(?i)\\b([A-Z][a-z]+)'s hair (?:is|was) ([a-z]+)", m ->
            add.accept(cap(m.group(1)), fact("hairColor", m.group(2), FactIntent.ASSERTION, m.group())));
        match(text, "(?i)\\b([A-Z][a-z]+)\\s+(?:loses|lost) (?:his |her |their )?(left|right) arm", m ->
            add.accept(cap(m.group(1)), fact(m.group(2).toLowerCase() + "ArmStatus", "lost", FactIntent.CHANGE, m.group())));
        match(text, "(?i)\\b([A-Z][a-z]+)\\s+(?:holds|held|wields|wielded).{0,30}(?:each hand|both hands|two hands)", m ->
            add.accept(cap(m.group(1)), fact("handUse", "both hands", FactIntent.ASSERTION, m.group())));
        match(text, "(?i)\\b([A-Z][a-z]+)\\s+(?:moves|moved|travels|traveled|arrives|arrived) (?:to|in|at) ([A-Z][A-Za-z -]+?)(?:[.,]|$)", m ->
            add.accept(cap(m.group(1)), fact("location", m.group(2).strip(), FactIntent.CHANGE, m.group())));
        match(text, "(?i)\\b([A-Z][a-z]+)\\s+(?:is|was) (?:in|at) ([A-Z][A-Za-z -]+?)(?:[.,]|$)", m ->
            add.accept(cap(m.group(1)), fact("location", m.group(2).strip(), FactIntent.ASSERTION, m.group())));
        match(text, "(?i)\\b([A-Z][a-z]+)\\s+(?:wears|wore|puts on) (?:a |an |the )?([^.,]+)", m ->
            add.accept(cap(m.group(1)), fact("clothing", m.group(2).strip(), FactIntent.CHANGE, m.group())));
        match(text, "(?i)\\b([A-Z][a-z]+)\\s+(?:dies|died|is dead|was killed)", m ->
            add.accept(cap(m.group(1)), fact("lifeStatus", "dead", FactIntent.CHANGE, m.group())));
        match(text, "(?i)\\b([A-Z][a-z]+)\\s+(?:is alive|survives|survived)", m ->
            add.accept(cap(m.group(1)), fact("lifeStatus", "alive", FactIntent.ASSERTION, m.group())));

        if (facts.isEmpty()) {
            Matcher name = Pattern.compile("\\b([A-Z][a-z]{2,})\\b").matcher(text);
            if (name.find()) facts.put(name.group(1), List.of(
                new ExtractedFact("mention", "present", KnowledgeKind.UNKNOWN, FactIntent.ASSERTION, name.group())));
        }
        ExtractionResult result = new ExtractionResult(facts.entrySet().stream()
            .map(entry -> new ExtractedEntity(entry.getKey(), EntityType.CHARACTER, entry.getValue(), List.of()))
            .toList());
        try { return objectMapper.writeValueAsString(result); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize extraction", exception); }
    }

    private static ExtractedFact fact(String key, String value, FactIntent intent, String evidence) {
        return new ExtractedFact(key, value.strip().toLowerCase(), KnowledgeKind.FACT, intent, evidence);
    }
    private static String cap(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(); }
    private static void match(String text, String regex, java.util.function.Consumer<Matcher> consumer) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) consumer.accept(matcher);
    }
}
