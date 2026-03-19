package uk.co.quietadmin.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.quietadmin.domain.group.MemberService;
import uk.co.quietadmin.domain.group.dto.MemberDto;
import uk.co.quietadmin.domain.team.dto.TeamDto;
import uk.co.quietadmin.service.team.TeamService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final MemberService memberService;

    /* ======================================================
       LIST
       ====================================================== */

    @GetMapping
    public ResponseEntity<List<TeamDto>> list(Principal principal) {
        return ResponseEntity.ok(
                teamService.listTeams(principal.getName())
        );
    }

    /* ======================================================
       CREATE
       ====================================================== */

    @PostMapping
    public ResponseEntity<TeamDto> create(
            Principal principal,
            @RequestBody CreateTeamRequest request
    ) {
        return ResponseEntity.ok(
                teamService.createTeam(
                        principal.getName(),
                        request.name(),
                        request.description()
                )
        );
    }

    @PutMapping("/{teamId}")
    public ResponseEntity<TeamDto> update(
            Principal principal,
            @PathVariable Long teamId,
            @RequestBody CreateTeamRequest request
    ) {
        return ResponseEntity.ok(
                teamService.updateTeam(
                        principal.getName(),
                        teamId,
                        request.name(),
                        request.description()
                )
        );
    }

    /* ======================================================
       DELETE
       ====================================================== */

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> delete(
            Principal principal,
            @PathVariable Long teamId
    ) {
        teamService.deleteTeam(principal.getName(), teamId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<Void> addMember(
            Principal principal,
            @PathVariable Long teamId,
            @RequestBody AddMemberRequest request
    ) {
        teamService.addMember(
                principal.getName(),
                teamId,
                request.userId()
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            Principal principal,
            @PathVariable Long teamId,
            @PathVariable Long userId
    ) {
        teamService.removeMember(
                principal.getName(),
                teamId,
                userId
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<MemberDto>> listMembers(
            Principal principal,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(
                teamService.getMembers(principal.getName(), teamId)
        );
    }

    /* ======================================================
       REQUEST RECORD
       ====================================================== */

    public record CreateTeamRequest(
            String name,
            String description
    ) {}

    public record AddMemberRequest(Long userId) {}
}