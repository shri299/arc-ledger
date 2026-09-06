package io.arcledger.security;

import org.springframework.stereotype.Service;

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

    public Decision check(String key, int limit, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long startsAt = now - Math.floorMod(now, windowSeconds);
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
        windows.clear();
    }

    private void cleanup(long oldestStart) {
        windows.entrySet().removeIf(entry -> entry.getValue().startsAt() < oldestStart);
    }
}
