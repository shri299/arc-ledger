package io.arcledger.repository;

import io.arcledger.domain.SecurityAuditEvent;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, UUID> {
    Page<SecurityAuditEvent> findByActorId(UUID actorId, Pageable pageable);
    long deleteByCreatedAtBefore(Instant cutoff);
}
