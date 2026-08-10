package io.arcledger.service;

import io.arcledger.domain.*;
import io.arcledger.repository.*;
import io.arcledger.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NarrativePipelineIntegrationTest {
    @Autowired StoryService storyService;
    @Autowired SceneService sceneService;
    @Autowired NarrativeEntityRepository entityRepository;
    @Autowired EntityFactRepository factRepository;
    @Autowired EntityStateVersionRepository versionRepository;
    @Autowired ConsistencyResultRepository consistencyRepository;
    @Autowired NarrativeQuestionAnsweringService answeringService;

    @Test void versionsStateRejectsPlotHoleAndAnswersFromCurrentCanon() {
        Story story = storyService.create("The Last Meridian", "Continuity test");
        Chapter chapter = storyService.addChapter(story.getId(), 1, "The Siege");
        Scene establishing = sceneService.create(story.getId(), chapter.getId(), 1,
            "John has black hair. John is in London.");
        Scene injury = sceneService.create(story.getId(), chapter.getId(), 2,
            "John loses his left arm during the battle.");
        sceneService.create(story.getId(), chapter.getId(), 3, "John moved to Paris.");
        Scene contradiction = sceneService.create(story.getId(), chapter.getId(), 4,
            "John holds one sword in each hand.");

        NarrativeEntity john = entityRepository.findByStoryIdAndNormalizedName(story.getId(), "john").orElseThrow();
        Map<String, EntityFact> current = new HashMap<>();
        factRepository.findByEntityIdAndActiveTrueOrderByKeyAsc(john.getId()).forEach(f -> current.put(f.getKey(), f));

        assertThat(establishing.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(injury.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(current.get("leftArmStatus").getValue()).isEqualTo("lost");
        assertThat(current.get("location").getValue()).isEqualTo("paris");
        assertThat(current).doesNotContainKey("handUse");
        assertThat(versionRepository.findByEntityIdOrderByVersionAsc(john.getId())).hasSize(3);
        List<EntityFact> history = factRepository.findByEntityIdOrderByCreatedAtAsc(john.getId());
        assertThat(history).hasSize(4);
        assertThat(history).filteredOn(f -> f.getKey().equals("location") && !f.isActive())
            .singleElement().extracting(EntityFact::getSupersededByFactId).isNotNull();
        assertThat(consistencyRepository.findBySceneIdOrderByCreatedAtAsc(contradiction.getId()))
            .extracting(ConsistencyResult::getStatus).contains(ValidationStatus.CONTRADICTION);

        NarrativeQuestionAnsweringService.Answer answer = answeringService.answer(story.getId(), "What happened to John's left arm?");
        assertThat(answer.status()).isEqualTo("GROUNDED");
        assertThat(answer.answer()).contains("lost");
        assertThat(answer.sources()).isNotEmpty();
    }
}
