package uk.co.quietadmin.api.rota.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class UpdateRotaRequest {

    private String name;
    private LocalDate rotaDate;
    private Long teamId;

    private List<CreateRotaAssignmentRequest> assignments;
}
