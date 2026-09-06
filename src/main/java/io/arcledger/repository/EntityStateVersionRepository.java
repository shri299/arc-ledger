package io.arcledger.repository;
import io.arcledger.domain.EntityStateVersion;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EntityStateVersionRepository extends JpaRepository<EntityStateVersion, UUID> {
    List<EntityStateVersion> findByEntityIdOrderByVersionAsc(UUID entityId);
    Page<EntityStateVersion> findByEntityId(UUID entityId, Pageable pageable);
    Optional<EntityStateVersion> findFirstByEntityIdOrderByVersionDesc(UUID entityId);
}
