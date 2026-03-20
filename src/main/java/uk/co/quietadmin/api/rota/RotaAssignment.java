package uk.co.quietadmin.api.rota;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "rota_assignment")
public class RotaAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rota_id", nullable = false)
    private Rota rota;

    @Column(nullable = false, length = 120)
    private String roleName;

    @Column
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RotaAssignmentStatus status = RotaAssignmentStatus.ASSIGNED;

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() {
        return id;
    }

    public Rota getRota() {
        return rota;
    }

    public String getRoleName() {
        return roleName;
    }

    public Long getUserId() {
        return userId;
    }

    public RotaAssignmentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setRota(Rota rota) {
        this.rota = rota;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setStatus(RotaAssignmentStatus status) {
        this.status = status;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}