package uk.co.quietadmin.domain.group;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
@Table(
        name = "membership",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "group_id"})
)
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private String role; // ADMIN / MEMBER

    @Column(name = "joined_at")
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
    }
}