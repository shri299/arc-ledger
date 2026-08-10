package io.arcledger.repository;
import io.arcledger.domain.ConsistencyResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ConsistencyResultRepository extends JpaRepository<ConsistencyResult, UUID> {
    List<ConsistencyResult> findBySceneIdOrderByCreatedAtAsc(UUID sceneId);
}
