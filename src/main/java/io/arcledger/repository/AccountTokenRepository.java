package io.arcledger.repository;

import io.arcledger.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.*;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {
    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, AccountTokenPurpose purpose);
    long deleteByUserIdAndPurpose(UUID userId, AccountTokenPurpose purpose);
    long deleteByExpiresAtBefore(Instant cutoff);
}
