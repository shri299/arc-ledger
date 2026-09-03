package io.arcledger.repository;
import io.arcledger.domain.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StoryRepository extends JpaRepository<Story, UUID> {
    Optional<Story> findByIdAndOwnerId(UUID id, UUID ownerId);
    List<Story> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
}
