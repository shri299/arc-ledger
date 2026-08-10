package io.arcledger.repository;
import io.arcledger.domain.EntityFact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EntityFactRepository extends JpaRepository<EntityFact, UUID> {
    List<EntityFact> findByEntityIdAndActiveTrueOrderByKeyAsc(UUID entityId);
    List<EntityFact> findByEntityIdOrderByCreatedAtAsc(UUID entityId);
}
