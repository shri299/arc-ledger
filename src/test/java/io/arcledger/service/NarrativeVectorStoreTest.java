package io.arcledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.*;
import io.arcledger.repository.SyntheticQuestionRepository;
import io.arcledger.service.impl.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NarrativeVectorStoreTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HashEmbeddingService embeddings = new HashEmbeddingService();
    private final SyntheticQuestionRepository repository = mock(SyntheticQuestionRepository.class);
    private final JpaNarrativeVectorStore store = new JpaNarrativeVectorStore(repository, embeddings, mapper, .65, .15, .10, .10);

    @Test void currentVersionOutranksObsoleteVersionAndMetadataFilterApplies() throws Exception {
        Story story = new Story("Test", ""); Chapter chapter = new Chapter(story, 1, "One");
        Scene firstScene = new Scene(story, chapter, 1, "John is in London.");
        Scene secondScene = new Scene(story, chapter, 2, "John moved to Paris.");
        NarrativeEntity john = new NarrativeEntity(story, "John", EntityType.CHARACTER);
        EntityStateVersion v1 = new EntityStateVersion(john, firstScene, john.nextVersion(), "{}", "{}");
        EntityStateVersion v2 = new EntityStateVersion(john, secondScene, john.nextVersion(), "{}", "{}");
        String question = "Where is John currently located?";
        SyntheticQuestion obsolete = new SyntheticQuestion(v1, question, "John — location: London.", mapper.writeValueAsString(embeddings.embed(question)));
        obsolete.markObsolete();
        SyntheticQuestion current = new SyntheticQuestion(v2, question, "John — location: Paris.", mapper.writeValueAsString(embeddings.embed(question)));
        when(repository.findByStoryId(story.getId())).thenReturn(List.of(obsolete, current));

        List<RetrievalHit> hits = store.search(story.getId(), question, john.getId(), 5);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).current()).isTrue();
        assertThat(hits.get(0).answer()).contains("Paris");
        assertThat(store.search(story.getId(), question, UUID.randomUUID(), 5)).isEmpty();
    }
}
