package io.arcledger.repository;
import io.arcledger.domain.SyntheticQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
public interface SyntheticQuestionRepository extends JpaRepository<SyntheticQuestion, UUID> {
    @Override
    @EntityGraph(attributePaths = {"entity", "stateVersion", "scene"})
    List<SyntheticQuestion> findAll();

    @EntityGraph(attributePaths = {"entity", "stateVersion", "scene"})
    List<SyntheticQuestion> findByStoryId(UUID storyId);
    List<SyntheticQuestion> findByEntityIdAndCurrentTrue(UUID entityId);
}
