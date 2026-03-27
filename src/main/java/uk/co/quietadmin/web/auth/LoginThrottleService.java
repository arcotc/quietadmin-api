package uk.co.quietadmin.web.auth;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.auth.LoginThrottle;
import uk.co.quietadmin.domain.auth.LoginThrottleRepository;
import uk.co.quietadmin.security.SecurityEventService;

import java.time.Instant;

@Service
@AllArgsConstructor
public class LoginThrottleService {
    private final LoginThrottleRepository repo;
    private final SecurityEventService securityEventService;

    // policy
    private final int maxFailures = 5;
    private final long windowSeconds = 600; // 10 minutes

    @Transactional
    public void assertLoginAllowed(String email, String ip) {
        repo.findByEmailAndIpAddress(email, ip).ifPresent(t -> {
            Instant now = Instant.now();

            if (t.getLockedUntil() != null && t.getLockedUntil().isAfter(now)) {
                securityEventService.authLocked(email, ip, "lock_active");
                throw new IllegalArgumentException("Too many attempts. Try again later.");
            }
        });
    }

    @Transactional
    public void recordFailure(String email, String ip) {
        Instant now = Instant.now();

        LoginThrottle t = repo.findByEmailAndIpAddress(email, ip).orElseGet(() -> {
            LoginThrottle nt = new LoginThrottle();
            nt.setEmail(email);
            nt.setIpAddress(ip);
            nt.setUpdatedAt(now);
            nt.setLockoutLevel(0); // NEW
            return nt;
        });

        // reset window if outside window
        if (t.getFirstFailedAt() == null ||
                t.getFirstFailedAt().isBefore(now.minusSeconds(windowSeconds))) {

            t.setFirstFailedAt(now);
            t.setUpdatedAt(now);
            t.setFailedCount(0);
        }

        t.setFailedCount(t.getFailedCount() + 1);

        if (t.getFailedCount() >= maxFailures) {

            int level = Math.min(t.getLockoutLevel() + 1, 3); // cap at level 3
            t.setLockoutLevel(level);

            long lockSeconds = lockDurationForLevel(level);

            t.setLockedUntil(now.plusSeconds(lockSeconds));

            // reset failure window after lock
            t.setFailedCount(0);
            t.setFirstFailedAt(null);

            securityEventService.authLocked(email, ip, "lock_level_" + level);
        }

        repo.save(t);
    }

    @Transactional
    public void recordSuccess(String email, String ip) {
        repo.findByEmailAndIpAddress(email, ip).ifPresent(t -> {
            t.setFailedCount(0);
            t.setFirstFailedAt(null);
            t.setLockedUntil(null);
            t.setLockoutLevel(0); // 🔥 reset escalation
            repo.save(t);
        });
    }

    private long lockDurationForLevel(int level) {
        return switch (level) {
            case 1 -> 900;     // 15 minutes
            case 2 -> 3600;    // 1 hour
            default -> 21600;  // 6 hours
        };
    }
}