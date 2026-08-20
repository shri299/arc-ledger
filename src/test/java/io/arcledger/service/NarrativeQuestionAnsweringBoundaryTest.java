package io.arcledger.service;

import io.arcledger.domain.Chapter;
import io.arcledger.domain.Story;
import io.arcledger.service.impl.SceneService;
import io.arcledger.service.impl.StoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NarrativeQuestionAnsweringBoundaryTest {
    @Autowired StoryService storyService;
    @Autowired SceneService sceneService;
    @Autowired NarrativeQuestionAnsweringService answeringService;

    @Test
    void answersAfterSceneTransactionHasClosed() {
        Story story = storyService.create("Detached Memory", "Transaction-boundary regression test");
        Chapter chapter = storyService.addChapter(story.getId(), 1, "Arrival");
        sceneService.create(story.getId(), chapter.getId(), 1,
            "John has black hair. John is in London.");

        NarrativeQuestionAnsweringService.Answer answer =
            answeringService.answer(story.getId(), "Where is John?");

        assertThat(answer.status()).isEqualTo("GROUNDED");
        assertThat(answer.answer()).containsIgnoringCase("london");
        assertThat(answer.sources()).isNotEmpty().allSatisfy(source -> {
            assertThat(source.entityName()).isEqualTo("John");
            assertThat(source.sceneId()).isNotNull();
            assertThat(source.stateVersionId()).isNotNull();
        });
    }
}
