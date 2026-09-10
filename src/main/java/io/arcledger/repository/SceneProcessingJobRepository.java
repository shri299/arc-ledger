package io.arcledger.repository;

import io.arcledger.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface SceneProcessingJobRepository extends JpaRepository<SceneProcessingJob, UUID> {
    Optional<SceneProcessingJob> findBySceneId(UUID sceneId);

    @Query(value = """
        SELECT CAST(job.id AS VARCHAR)
          FROM scene_processing_jobs job
          JOIN scenes scene ON scene.id = job.scene_id
          JOIN chapters chapter ON chapter.id = scene.chapter_id
         WHERE job.status IN ('QUEUED', 'RETRYING') AND job.available_at <= :now
           AND NOT EXISTS (
               SELECT 1
                 FROM scene_processing_jobs active_job
                 JOIN scenes active_scene ON active_scene.id = active_job.scene_id
                WHERE active_scene.story_id = scene.story_id AND active_job.status = 'PROCESSING'
           )
           AND NOT EXISTS (
               SELECT 1
                 FROM scene_processing_jobs earlier_job
                 JOIN scenes earlier_scene ON earlier_scene.id = earlier_job.scene_id
                 JOIN chapters earlier_chapter ON earlier_chapter.id = earlier_scene.chapter_id
                WHERE earlier_scene.story_id = scene.story_id
                  AND earlier_job.status IN ('QUEUED', 'PROCESSING', 'RETRYING', 'DEAD_LETTER')
                  AND (earlier_chapter.chapter_number < chapter.chapter_number
                    OR (earlier_chapter.chapter_number = chapter.chapter_number
                      AND earlier_scene.sequence_number < scene.sequence_number))
           )
         ORDER BY job.created_at
         LIMIT 1
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<String> findClaimableId(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from SceneProcessingJob job where job.id = :id")
    Optional<SceneProcessingJob> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from SceneProcessingJob job where job.status = :status and job.lockedAt < :cutoff")
    List<SceneProcessingJob> findStale(@Param("status") ProcessingJobStatus status,
                                       @Param("cutoff") Instant cutoff);

    long deleteByStatusAndUpdatedAtBefore(ProcessingJobStatus status, Instant cutoff);
}
