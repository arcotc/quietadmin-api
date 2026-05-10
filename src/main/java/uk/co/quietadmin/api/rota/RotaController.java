package uk.co.quietadmin.api.rota;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.api.rota.dto.CreateRotaRequest;
import uk.co.quietadmin.api.rota.dto.ReplaceAssignmentRequest;
import uk.co.quietadmin.api.rota.dto.RotaResponse;
import uk.co.quietadmin.api.rota.dto.UpdateRotaRequest;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/rotas")
public class RotaController {

    private final RotaService rotaService;

    public RotaController(RotaService rotaService) {
        this.rotaService = rotaService;
    }

    @GetMapping
    public ResponseEntity<List<RotaResponse>> listUpcoming(Principal principal) {
        return ResponseEntity.ok(
                rotaService.listUpcoming(principal.getName())
        );
    }

    @GetMapping("/{rotaId}")
    public ResponseEntity<RotaResponse> getRota(
            Principal principal,
            @PathVariable Long rotaId
    ) {
        try {
            RotaResponse rota = rotaService.getById(principal.getName(), rotaId);
            return ResponseEntity.ok(
                    rota
            );
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{rotaId}")
    public ResponseEntity<Void> delete(
            Principal principal,
            @PathVariable Long rotaId
    ) {
        rotaService.delete(principal.getName(), rotaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<RotaResponse> create(
            Principal principal,
            @Valid @RequestBody CreateRotaRequest request
    ) {
        return ResponseEntity.ok(
                rotaService.create(principal.getName(), request)
        );
    }

    @PutMapping("/{rotaId}")
    public ResponseEntity<RotaResponse> update(
            Principal principal,
            @PathVariable Long rotaId,
            @Valid @RequestBody UpdateRotaRequest request
    ) {
        return ResponseEntity.ok(
                rotaService.update(
                        principal.getName(),
                        rotaId,
                        request
                )
        );
    }

    @PostMapping("/{rotaId}/assignments/{assignmentId}/decline")
    public ResponseEntity<RotaResponse> decline(
            Principal principal,
            @PathVariable Long rotaId,
            @PathVariable Long assignmentId
    ) {
        return ResponseEntity.ok(
                rotaService.declineAssignment(
                        principal.getName(),
                        rotaId,
                        assignmentId
                )
        );
    }

    @PostMapping("/{rotaId}/share")
    public ResponseEntity<Void> share(
            Principal principal,
            @PathVariable Long rotaId
    ) {
        rotaService.shareRota(principal.getName(), rotaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/decline")
    public ResponseEntity<Void> declineByToken(@RequestParam String token) {
        rotaService.declineByToken(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/decline-all")
    public ResponseEntity<Void> declineAllByToken(@RequestParam String token) {
        rotaService.declineAllByToken(token);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{rotaId}/assignments/{assignmentId}")
    public ResponseEntity<RotaResponse> replaceAssignment(
            Principal principal,
            @PathVariable Long rotaId,
            @PathVariable Long assignmentId,
            @Valid @RequestBody ReplaceAssignmentRequest request
    ) {
        return ResponseEntity.ok(
                rotaService.replaceAssignment(
                        principal.getName(),
                        rotaId,
                        assignmentId,
                        request
                )
        );
    }
}
