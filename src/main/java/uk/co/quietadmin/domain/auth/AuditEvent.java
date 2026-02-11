package uk.co.quietadmin.domain.auth;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name="audit_event")
@Data
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id")
    private Long userId;

    @Column(name="group_id")
    private Long groupId;

    @Column(name="event_type", nullable=false, length=64)
    private String eventType;

    @Column(nullable=false)
    private boolean success = true;

    private String message;

    @Column(name="ip_address")
    private String ipAddress;

    @Column(name="user_agent")
    private String userAgent;

    @Column(name="metadata_json", columnDefinition="json")
    private String metadataJson;

    @Column(name="created_at")
    private Instant createdAt;
}