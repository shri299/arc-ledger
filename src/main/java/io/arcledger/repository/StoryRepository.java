package io.arcledger.repository;
import io.arcledger.domain.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface StoryRepository extends JpaRepository<Story, UUID> {}
