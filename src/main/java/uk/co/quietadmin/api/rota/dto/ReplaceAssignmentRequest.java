package uk.co.quietadmin.api.rota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReplaceAssignmentRequest {

    @NotBlank
    private String roleName;

    @NotNull
    private Long userId;

    public String getRoleName() {
        return roleName;
    }

    public Long getUserId() {
        return userId;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}