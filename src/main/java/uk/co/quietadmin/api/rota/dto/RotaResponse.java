package uk.co.quietadmin.api.rota.dto;

import uk.co.quietadmin.api.rota.RotaAssignmentStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RotaResponse {

    private Long id;
    private String name;
    private LocalDate rotaDate;
    private Long groupId;
    private Long teamId;
    private List<RotaAssignmentResponse> assignments = new ArrayList<>();

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

    public List<RotaAssignmentResponse> getAssignments() {
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

    public void setAssignments(List<RotaAssignmentResponse> assignments) {
        this.assignments = assignments;
    }

    public static class RotaAssignmentResponse {
        private Long id;
        private String roleName;
        private Long userId;
        private String displayName;
        private Long preferredTeamId;
        private String preferredTeamName;
        private RotaAssignmentStatus status;
        private boolean assignedToCurrentUser;

        public Long getId() {
            return id;
        }

        public String getRoleName() {
            return roleName;
        }

        public Long getUserId() {
            return userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Long getPreferredTeamId() {
            return preferredTeamId;
        }

        public String getPreferredTeamName() {
            return preferredTeamName;
        }

        public RotaAssignmentStatus getStatus() {
            return status;
        }

        public boolean isAssignedToCurrentUser() {
            return assignedToCurrentUser;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public void setPreferredTeamId(Long preferredTeamId) {
            this.preferredTeamId = preferredTeamId;
        }

        public void setPreferredTeamName(String preferredTeamName) {
            this.preferredTeamName = preferredTeamName;
        }

        public void setStatus(RotaAssignmentStatus status) {
            this.status = status;
        }

        public void setAssignedToCurrentUser(boolean assignedToCurrentUser) {
            this.assignedToCurrentUser = assignedToCurrentUser;
        }
    }
}
