package io.arcledger.repository;
import io.arcledger.domain.SyntheticQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SyntheticQuestionRepository extends JpaRepository<SyntheticQuestion, UUID> {
    List<SyntheticQuestion> findByStoryId(UUID storyId);
    List<SyntheticQuestion> findByEntityIdAndCurrentTrue(UUID entityId);
}
