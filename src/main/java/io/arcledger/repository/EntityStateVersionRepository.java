package io.arcledger.repository;
import io.arcledger.domain.EntityStateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EntityStateVersionRepository extends JpaRepository<EntityStateVersion, UUID> {
    List<EntityStateVersion> findByEntityIdOrderByVersionAsc(UUID entityId);
    Optional<EntityStateVersion> findFirstByEntityIdOrderByVersionDesc(UUID entityId);
}
