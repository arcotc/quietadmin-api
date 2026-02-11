package uk.co.quietadmin.web.auth;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import uk.co.quietadmin.domain.auth.AuditEvent;
import uk.co.quietadmin.domain.auth.AuditEventRepository;

@Service
public class AuditService {

    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void log(String type, boolean success, String message, Long userId, Long groupId,
                    String ip, String ua, String metadataJson) {

        AuditEvent e = new AuditEvent();
        e.setEventType(type);
        e.setSuccess(success);
        e.setMessage(message);
        e.setUserId(userId);
        e.setGroupId(groupId);
        e.setIpAddress(ip);
        e.setUserAgent(ua);
        e.setMetadataJson(metadataJson);

        repo.save(e);
    }
}