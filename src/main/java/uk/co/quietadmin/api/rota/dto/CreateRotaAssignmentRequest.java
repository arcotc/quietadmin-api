package uk.co.quietadmin.api.rota.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateRotaAssignmentRequest {

    @NotBlank
    private String roleName;

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