package io.arcledger.repository;

import io.arcledger.domain.AccountRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
import java.time.Instant;

public interface AccountRecoveryCodeRepository extends JpaRepository<AccountRecoveryCode, UUID> {
    Optional<AccountRecoveryCode> findByUserIdAndCodeHash(UUID userId, String codeHash);
    long deleteByUserId(UUID userId);
    long countByUserIdAndConsumedAtIsNull(UUID userId);
    long deleteByConsumedAtBefore(Instant cutoff);
}
