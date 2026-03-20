package uk.co.quietadmin.api.rota;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rota")
public class Rota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private LocalDate rotaDate;

    @Column(nullable = false)
    private Long groupId;

    @Column
    private Long teamId;

    @Column(nullable = false)
    private Long createdByUserId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "rota", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RotaAssignment> assignments = new ArrayList<>();

    public void addAssignment(RotaAssignment assignment) {
        assignment.setRota(this);
        this.assignments.add(assignment);
    }

    public void clearAssignments() {
        this.assignments.forEach(a -> a.setRota(null));
        this.assignments.clear();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getRotaDate() {
        return rotaDate;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public List<RotaAssignment> getAssignments() {
        return assignments;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRotaDate(LocalDate rotaDate) {
        this.rotaDate = rotaDate;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setAssignments(List<RotaAssignment> assignments) {
        this.assignments = assignments;
    }
}