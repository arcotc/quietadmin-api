package uk.co.quietadmin.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class SecurityEventService {

    public void event(String type, Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ts", Instant.now().toString());
        payload.put("type", type);
        payload.putAll(fields);

        log.info("SECURITY_EVENT {}", payload);
    }

    public void authFailure(String email, String ip, String reason) {
        event("AUTH_LOGIN_FAILURE", Map.of(
                "email", email,
                "ip", ip,
                "reason", reason
        ));
    }

    public void authSuccess(Long userId, String email, String ip) {
        event("AUTH_LOGIN_SUCCESS", Map.of(
                "userId", userId,
                "email", email,
                "ip", ip
        ));
    }

    public void authLocked(String email, String ip, String reason) {
        event("AUTH_LOGIN_LOCKED", Map.of(
                "email", email,
                "ip", ip,
                "reason", reason
        ));
    }

    public void sessionAnomaly(Long userId, String ip, String reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ip", ip);
        fields.put("reason", reason);
        if (userId != null) {
            fields.put("userId", userId);
        }

        event("AUTH_SESSION_ANOMALY", fields);
    }

    public void rateLimitHit(String key, String ip) {
        event("RATE_LIMIT_HIT", Map.of(
                "key", key,
                "ip", ip
        ));
    }

    public void audit(String type, Long actorUserId, Long groupId, Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (actorUserId != null) payload.put("actorUserId", actorUserId);
        if (groupId != null) payload.put("groupId", groupId);
        payload.putAll(fields);

        event(type, payload);
    }

    public static String getIp(HttpServletRequest request) {
        return IpResolver.resolve(request);
    }
}