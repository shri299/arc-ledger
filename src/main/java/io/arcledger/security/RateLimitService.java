package io.arcledger.security;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RateLimitService {
    public record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {}
    private record Window(long startsAt, int count) {}

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();
    private final JdbcTemplate jdbcTemplate;
    private final boolean distributed;

    public RateLimitService(JdbcTemplate jdbcTemplate,
                            @Value("${arcledger.persistence.distributed-rate-limits:true}") boolean distributed) {
        this.jdbcTemplate = jdbcTemplate;
        this.distributed = distributed;
    }

    public Decision check(String key, int limit, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long startsAt = now - Math.floorMod(now, windowSeconds);
        if (distributed) return checkDatabase(key, limit, windowSeconds, now, startsAt);
        AtomicReference<Window> selected = new AtomicReference<>();
        windows.compute(key, (ignored, current) -> {
            Window next = current == null || current.startsAt() != startsAt
                ? new Window(startsAt, 1) : new Window(startsAt, current.count() + 1);
            selected.set(next);
            return next;
        });
        if ((checks.incrementAndGet() & 1023) == 0) cleanup(startsAt - 3600);
        Window window = selected.get();
        boolean allowed = window.count() <= limit;
        int remaining = Math.max(0, limit - window.count());
        long retryAfter = Math.max(1, startsAt + windowSeconds - now);
        return new Decision(allowed, limit, remaining, retryAfter);
    }

    public void clear() {
        if (distributed) jdbcTemplate.update("DELETE FROM rate_limit_windows");
        else windows.clear();
    }

    private Decision checkDatabase(String key, int limit, long windowSeconds, long now, long startsAt) {
        Integer count = jdbcTemplate.queryForObject("""
            INSERT INTO rate_limit_windows(rate_key, window_start, hit_count, expires_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT (rate_key, window_start)
            DO UPDATE SET hit_count = rate_limit_windows.hit_count + 1,
                          expires_at = EXCLUDED.expires_at
            RETURNING hit_count
            """, Integer.class, key, startsAt, startsAt + windowSeconds);
        if ((checks.incrementAndGet() & 1023) == 0) {
            jdbcTemplate.update("DELETE FROM rate_limit_windows WHERE expires_at < ?", now);
        }
        int hits = count == null ? limit + 1 : count;
        return new Decision(hits <= limit, limit, Math.max(0, limit - hits),
            Math.max(1, startsAt + windowSeconds - now));
    }

    private void cleanup(long oldestStart) {
        windows.entrySet().removeIf(entry -> entry.getValue().startsAt() < oldestStart);
    }
}
