package io.arcledger.service;

import io.arcledger.domain.*;
import io.arcledger.service.impl.PgVectorNarrativeVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class PgVectorNarrativeVectorStoreTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EmbeddingService embeddings = text -> new double[] {1, 0};
    private final PgVectorNarrativeVectorStore store = new PgVectorNarrativeVectorStore(
        jdbcTemplate, embeddings, 3, 50, .65, .15, .10, .10);

    @Test
    void writesNativeVectorLiteralThroughPostgresCast() {
        Story story = new Story("Test", "");
        Chapter chapter = new Chapter(story, 1, "One");
        Scene scene = new Scene(story, chapter, 1, "John is in Paris.");
        NarrativeEntity entity = new NarrativeEntity(story, "John", EntityType.CHARACTER);
        EntityStateVersion version = new EntityStateVersion(entity, scene, 1, "{}", "{}");
        SyntheticQuestion question = new SyntheticQuestion(version, "Where is John?", "John is in Paris.");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        store.index(question, new double[] {1.0, -0.5, 0.25});

        verify(jdbcTemplate).update(contains("CAST(? AS vector)"),
            aryEq(new Object[] {"[1.0,-0.5,0.25]", question.getId()}));
    }

    @Test
    void rejectsEmbeddingDimensionMismatchBeforeDatabaseWrite() {
        assertThatThrownBy(() -> store.search(java.util.UUID.randomUUID(), "query", null, 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Expected 3 embedding dimensions");
        verifyNoInteractions(jdbcTemplate);
    }
}
