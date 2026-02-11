package uk.co.quietadmin.web.auth;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.auth.LoginThrottle;
import uk.co.quietadmin.domain.auth.LoginThrottleRepository;

import java.time.Instant;

@Service
public class LoginThrottleService {

    private final LoginThrottleRepository repo;

    // policy
    private final int maxFailures = 5;
    private final long windowSeconds = 600;      // 10 minutes
    private final long lockSeconds = 900;        // 15 minutes

    public LoginThrottleService(LoginThrottleRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void assertLoginAllowed(String email, String ip) {
        repo.findByEmailAndIpAddress(email, ip).ifPresent(t -> {
            Instant now = Instant.now();
            if (t.getLockedUntil() != null && t.getLockedUntil().isAfter(now)) {
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
            return nt;
        });

        // reset window if outside window
        if (t.getFirstFailedAt() == null || t.getFirstFailedAt().isBefore(now.minusSeconds(windowSeconds))) {
            t.setFirstFailedAt(now);
            t.setFailedCount(0);
        }

        t.setFailedCount(t.getFailedCount() + 1);

        if (t.getFailedCount() >= maxFailures) {
            t.setLockedUntil(now.plusSeconds(lockSeconds));
        }

        repo.save(t);
    }

    @Transactional
    public void recordSuccess(String email, String ip) {
        repo.findByEmailAndIpAddress(email, ip).ifPresent(t -> {
            t.setFailedCount(0);
            t.setFirstFailedAt(null);
            t.setLockedUntil(null);
            repo.save(t);
        });
    }
}