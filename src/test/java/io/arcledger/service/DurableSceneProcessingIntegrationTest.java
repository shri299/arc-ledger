package io.arcledger.service;

import io.arcledger.domain.*;
import io.arcledger.repository.SceneProcessingJobRepository;
import io.arcledger.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:arcledger-durable-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "arcledger.processing.background-enabled=true",
    "arcledger.processing.poll-delay-ms=3600000"
})
@DirtiesContext
class DurableSceneProcessingIntegrationTest {
    @Autowired StoryService storyService;
    @Autowired SceneService sceneService;
    @Autowired SceneProcessingCoordinator coordinator;
    @Autowired SceneProcessingJobRepository jobs;

    @Test
    void persistsClaimsAndCompletesSceneWorkAtomically() {
        Story story = storyService.create("Queued story", "Durable processing test");
        Chapter chapter = storyService.addChapter(story.getId(), 1, "Opening");
        Scene scene = sceneService.create(story.getId(), chapter.getId(), 1, "Mira enters the observatory.");
        Scene secondScene = sceneService.create(story.getId(), chapter.getId(), 2, "Mira opens the star ledger.");

        SceneProcessingJob queued = jobs.findBySceneId(scene.getId()).orElseThrow();
        SceneProcessingJob secondQueued = jobs.findBySceneId(secondScene.getId()).orElseThrow();
        assertThat(sceneService.get(story.getId(), scene.getId()).getProcessingStatus()).isEqualTo(ProcessingStatus.QUEUED);
        assertThat(queued.getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);

        var claimed = coordinator.claimNext("integration-worker");
        assertThat(claimed).contains(queued.getId());
        assertThat(jobs.findById(queued.getId()).orElseThrow().getStatus()).isEqualTo(ProcessingJobStatus.PROCESSING);
        assertThat(coordinator.claimNext("competing-worker")).isEmpty();

        coordinator.processClaimed(queued.getId());
        assertThat(coordinator.claimNext("integration-worker")).contains(secondQueued.getId());
        coordinator.processClaimed(secondQueued.getId());

        Scene completedScene = sceneService.get(story.getId(), scene.getId());
        SceneProcessingJob completedJob = jobs.findById(queued.getId()).orElseThrow();
        assertThat(completedScene.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(completedJob.getStatus()).isEqualTo(ProcessingJobStatus.COMPLETED);
        assertThat(completedJob.getAttempts()).isEqualTo(1);
        assertThat(completedJob.getLockedAt()).isNull();
        assertThat(completedJob.getLockedBy()).isNull();
        assertThat(sceneService.get(story.getId(), secondScene.getId()).getProcessingStatus())
            .isEqualTo(ProcessingStatus.PROCESSED);

        jobs.delete(completedJob);
        coordinator.requeue(completedScene);
        assertThat(jobs.findBySceneId(scene.getId())).isEmpty();
        assertThat(sceneService.get(story.getId(), scene.getId()).getProcessingStatus())
            .isEqualTo(ProcessingStatus.PROCESSED);
    }
}
