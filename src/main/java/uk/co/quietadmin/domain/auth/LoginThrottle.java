package uk.co.quietadmin.domain.auth;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "login_throttle")
@Data
public class LoginThrottle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String email;

    @Column(name="ip_address", nullable=false)
    private String ipAddress;

    @Column(name="failed_count", nullable=false)
    private int failedCount;

    @Column(name="first_failed_at")
    private Instant firstFailedAt;

    @Column(name="locked_until")
    private Instant lockedUntil;

    @Column(name="updated_at")
    private Instant updatedAt;

    @Column(name="lockout_level", nullable=false)
    private int lockoutLevel;
}