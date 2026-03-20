package uk.co.quietadmin.api.rota.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class CreateRotaRequest {
    @NotBlank
    private String name;

    @NotNull
    @FutureOrPresent
    private LocalDate rotaDate;

    private Long teamId;

    private List<CreateRotaAssignmentRequest> assignments = new ArrayList<>();
}