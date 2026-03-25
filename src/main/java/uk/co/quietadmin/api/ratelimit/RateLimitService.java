package uk.co.quietadmin.api.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private final Cache<String, RateWindow> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2)) // cleanup safety
            .maximumSize(100_000)
            .build();

    public void assertAllowed(
            String key,
            int maxAttempts,
            Duration window,
            String message
    ) {
        Instant now = Instant.now();

        RateWindow existing = cache.getIfPresent(key);

        if (existing == null) {
            cache.put(key, new RateWindow(now, new AtomicInteger(1)));
            return;
        }

        synchronized (existing) {

            // 🔄 window expired → reset
            if (existing.windowStart.plus(window).isBefore(now)) {
                cache.put(key, new RateWindow(now, new AtomicInteger(1)));
                return;
            }

            int attempts = existing.count.incrementAndGet();

            if (attempts > maxAttempts) {
                throw new RateLimitExceededException(message);
            }
        }
    }

    private static class RateWindow {
        final Instant windowStart;
        final AtomicInteger count;

        RateWindow(Instant windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}