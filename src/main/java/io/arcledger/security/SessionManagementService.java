package io.arcledger.security;

import io.arcledger.domain.*;
import io.arcledger.repository.AccountSessionMetadataRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class SessionManagementService {
    public record SessionView(UUID id, String device, Instant createdAt, Instant lastSeenAt,
                              Instant expiresAt, boolean current) {}

    private final JdbcTemplate jdbc;
    private final AccountSessionMetadataRepository metadata;
    private final CurrentUserService currentUser;
    private final SecureTokenService tokens;
    private final PrivacyHashService privacyHashes;

    public SessionManagementService(JdbcTemplate jdbc, AccountSessionMetadataRepository metadata,
        CurrentUserService currentUser, SecureTokenService tokens, PrivacyHashService privacyHashes) {
        this.jdbc = jdbc;
        this.metadata = metadata;
        this.currentUser = currentUser;
        this.tokens = tokens;
        this.privacyHashes = privacyHashes;
    }

    @Transactional
    public void record(AppUser user, HttpServletRequest request) {
        if (request.getSession(false) == null) return;
        String sessionHash = tokens.hash(request.getSession(false).getId());
        AccountSessionMetadata entry = metadata.findById(sessionHash)
            .orElseGet(() -> metadata.save(new AccountSessionMetadata(sessionHash, user,
                deviceLabel(request.getHeader("User-Agent")), privacyHashes.hash(request.getRemoteAddr()))));
        entry.seen(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<SessionView> list(HttpServletRequest request) {
        AppUser user = currentUser.require();
        String currentSessionId = request.getSession(false) == null ? null : request.getSession(false).getId();
        Map<String, AccountSessionMetadata> byHash = new HashMap<>();
        metadata.findByUserId(user.getId()).forEach(item -> byHash.put(item.getSessionHash(), item));
        return jdbc.query("""
            SELECT primary_id, session_id, creation_time, last_access_time, expiry_time
              FROM spring_session
             WHERE principal_name = ?
             ORDER BY last_access_time DESC
            """, (row, index) -> {
                String sessionId = row.getString("session_id");
                AccountSessionMetadata item = byHash.get(tokens.hash(sessionId));
                Instant fallbackLastSeen = Instant.ofEpochMilli(row.getLong("last_access_time"));
                return new SessionView(UUID.fromString(row.getString("primary_id")),
                    item == null ? "Unknown device" : item.getDeviceLabel(),
                    Instant.ofEpochMilli(row.getLong("creation_time")),
                    item == null ? fallbackLastSeen : item.getLastSeenAt(),
                    Instant.ofEpochMilli(row.getLong("expiry_time")),
                    sessionId.equals(currentSessionId));
            }, user.getEmail());
    }

    @Transactional
    public boolean revoke(UUID primaryId, HttpServletRequest request) {
        AppUser user = currentUser.require();
        List<String> sessionIds = jdbc.query("SELECT session_id FROM spring_session WHERE primary_id = ? AND principal_name = ?",
            (row, index) -> row.getString(1), primaryId.toString(), user.getEmail());
        if (sessionIds.isEmpty()) return false;
        jdbc.update("DELETE FROM spring_session WHERE primary_id = ? AND principal_name = ?",
            primaryId.toString(), user.getEmail());
        sessionIds.forEach(sessionId -> metadata.deleteById(tokens.hash(sessionId)));
        return request.getSession(false) != null && sessionIds.contains(request.getSession(false).getId());
    }

    @Transactional
    public int revokeOthers(HttpServletRequest request) {
        AppUser user = currentUser.require();
        String currentSessionId = request.getSession(false) == null ? "" : request.getSession(false).getId();
        List<String> removed = jdbc.query(
            "SELECT session_id FROM spring_session WHERE principal_name = ? AND session_id <> ?",
            (row, index) -> row.getString(1), user.getEmail(), currentSessionId);
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ? AND session_id <> ?", user.getEmail(), currentSessionId);
        removed.forEach(sessionId -> metadata.deleteById(tokens.hash(sessionId)));
        return removed.size();
    }

    @Transactional
    public void forget(String sessionId) { metadata.deleteById(tokens.hash(sessionId)); }

    private static String deviceLabel(String userAgent) {
        String value = userAgent == null ? "" : userAgent;
        String browser = value.contains("Edg/") ? "Edge" : value.contains("Chrome/") ? "Chrome" :
            value.contains("Firefox/") ? "Firefox" : value.contains("Safari/") ? "Safari" : "Browser";
        String platform = value.contains("Android") ? "Android" : value.contains("iPhone") || value.contains("iPad") ? "iOS" :
            value.contains("Macintosh") ? "macOS" : value.contains("Windows") ? "Windows" : value.contains("Linux") ? "Linux" : "device";
        return browser + " on " + platform;
    }
}
