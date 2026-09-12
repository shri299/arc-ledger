package io.arcledger.repository;

import io.arcledger.domain.AppUser;
import io.arcledger.domain.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;

import java.util.*;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<AppUser> findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
        String email, String displayName, Pageable pageable);
    List<AppUser> findByAccountRole(AccountRole role);
}
