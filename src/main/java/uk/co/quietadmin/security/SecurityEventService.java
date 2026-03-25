package uk.co.quietadmin.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class SecurityEventService {

    public void log(String type, Map<String, Object> data) {
        log.info("SECURITY_EVENT type={} data={}", type, data);
    }

    public void authFailure(String email, String ip, String reason) {
        log("AUTH_FAILURE", Map.of(
                "email", email,
                "ip", ip,
                "reason", reason,
                "timestamp", Instant.now()
        ));
    }

    public void authSuccess(String email, String ip) {
        log("AUTH_SUCCESS", Map.of(
                "email", email,
                "ip", ip,
                "timestamp", Instant.now()
        ));
    }

    public void rateLimitHit(String key, String ip) {
        log("RATE_LIMIT", Map.of(
                "key", key,
                "ip", ip,
                "timestamp", Instant.now()
        ));
    }

    public void sessionAnomaly(Long userId, String ip, String reason) {
        log("SESSION_ANOMALY", Map.of(
                "userId", userId,
                "ip", ip,
                "reason", reason,
                "timestamp", Instant.now()
        ));
    }

    public static String getIp(HttpServletRequest request) {
        return IpResolver.resolve(request);
    }
}