package io.arcledger.repository;

import io.arcledger.domain.AccountSessionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
import java.time.Instant;

public interface AccountSessionMetadataRepository extends JpaRepository<AccountSessionMetadata, String> {
    List<AccountSessionMetadata> findByUserId(UUID userId);
    long deleteByUserId(UUID userId);
    long deleteByLastSeenAtBefore(Instant cutoff);
}
