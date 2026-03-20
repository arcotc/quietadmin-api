package uk.co.quietadmin.api.rota;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.api.rota.dto.*;
import uk.co.quietadmin.domain.customer.CurrentUserService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@AllArgsConstructor
public class RotaService {

    private final RotaRepository rotaRepository;
    private final RotaAssignmentRepository rotaAssignmentRepository;
    private final CurrentUserService currentUserService;

    /* ======================================================
       LIST
       ====================================================== */

    public List<RotaResponse> listUpcoming(String email) {

        Long groupId = currentUserService.getCurrentGroupId(email);

        return rotaRepository
                .findByGroupIdAndRotaDateGreaterThanEqualOrderByRotaDateAsc(
                        groupId,
                        LocalDate.now()
                )
                .stream()
                .map(r -> toResponse(email, r))
                .toList();
    }

    /* ======================================================
       GET
       ====================================================== */

    public RotaResponse getById(String email, Long rotaId) {

        Long groupId = currentUserService.getCurrentGroupId(email);

        Rota rota = rotaRepository.findById(rotaId)
                .orElseThrow(() -> new EntityNotFoundException("Rota not found"));

        if (!rota.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Invalid group");
        }

        return toResponse(email, rota);
    }

    /* ======================================================
       CREATE (ADMIN ONLY)
       ====================================================== */

    public RotaResponse create(String email, CreateRotaRequest request) {

        currentUserService.requireAdmin(email);

        Long groupId = currentUserService.getCurrentGroupId(email);
        Long currentUserId = currentUserService.getCurrentUserId(email);

        Rota rota = new Rota();
        rota.setName(request.getName());
        rota.setRotaDate(request.getRotaDate());
        rota.setGroupId(groupId);
        rota.setTeamId(request.getTeamId());
        rota.setCreatedByUserId(currentUserId);

        for (CreateRotaAssignmentRequest item : request.getAssignments()) {

            if (item.getUserId() != null) {

                Long userGroupId = currentUserService
                        .getMembership(email) // reuse logic
                        .getGroupId();

                // simple safety check (optional improvement later)
                if (!userGroupId.equals(groupId)) {
                    throw new AccessDeniedException("User not in group");
                }
            }

            RotaAssignment assignment = new RotaAssignment();
            assignment.setRoleName(item.getRoleName());
            assignment.setUserId(item.getUserId());
            assignment.setStatus(RotaAssignmentStatus.ASSIGNED);
            assignment.setUpdatedAt(OffsetDateTime.now());

            rota.addAssignment(assignment);
        }

        return toResponse(email, rotaRepository.save(rota));
    }

    /* ======================================================
       REPLACE (ADMIN ONLY)
       ====================================================== */

    public RotaResponse replaceAssignment(
            String email,
            Long rotaId,
            Long assignmentId,
            ReplaceAssignmentRequest request
    ) {

        currentUserService.requireAdmin(email);

        Long groupId = currentUserService.getCurrentGroupId(email);

        Rota rota = rotaRepository.findById(rotaId)
                .orElseThrow(() -> new EntityNotFoundException("Rota not found"));

        if (!rota.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Invalid group");
        }

        RotaAssignment assignment = rota.getAssignments().stream()
                .filter(a -> a.getId().equals(assignmentId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        assignment.setRoleName(request.getRoleName());
        assignment.setUserId(request.getUserId());
        assignment.setStatus(RotaAssignmentStatus.ASSIGNED);
        assignment.setUpdatedAt(OffsetDateTime.now());

        return toResponse(email, rotaRepository.save(rota));
    }

    /* ======================================================
       DECLINE (MEMBER ACTION)
       ====================================================== */

    public RotaResponse declineAssignment(
            String email,
            Long rotaId,
            Long assignmentId
    ) {

        Long groupId = currentUserService.getCurrentGroupId(email);
        Long currentUserId = currentUserService.getCurrentUserId(email);

        Rota rota = rotaRepository.findById(rotaId)
                .orElseThrow(() -> new EntityNotFoundException("Rota not found"));

        if (!rota.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Invalid group");
        }

        RotaAssignment assignment = rotaAssignmentRepository
                .findByIdAndUserId(assignmentId, currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        if (!assignment.getRota().getId().equals(rotaId)) {
            throw new IllegalArgumentException("Assignment mismatch");
        }

        assignment.setUserId(null);
        assignment.setStatus(RotaAssignmentStatus.DECLINED);
        assignment.setUpdatedAt(OffsetDateTime.now());

        rotaAssignmentRepository.save(assignment);

        return toResponse(email, rota);
    }

    /* ======================================================
       MAPPING
       ====================================================== */

    private RotaResponse toResponse(String email, Rota rota) {

        Long currentUserId = currentUserService.getCurrentUserId(email);

        RotaResponse response = new RotaResponse();
        response.setId(rota.getId());
        response.setName(rota.getName());
        response.setRotaDate(rota.getRotaDate());
        response.setGroupId(rota.getGroupId());
        response.setTeamId(rota.getTeamId());

        response.setAssignments(
                rota.getAssignments().stream().map(a -> {

                    RotaResponse.RotaAssignmentResponse item =
                            new RotaResponse.RotaAssignmentResponse();

                    item.setId(a.getId());
                    item.setRoleName(a.getRoleName());
                    item.setUserId(a.getUserId());

                    item.setDisplayName(
                            a.getUserId() == null
                                    ? null
                                    : currentUserService.getDisplayName(a.getUserId())
                    );

                    item.setStatus(a.getStatus());

                    item.setAssignedToCurrentUser(
                            a.getUserId() != null &&
                                    a.getUserId().equals(currentUserId)
                    );

                    return item;

                }).toList()
        );

        return response;
    }

    public RotaResponse update(
            String email,
            Long rotaId,
            UpdateRotaRequest request
    ) {

        currentUserService.requireAdmin(email);

        Long groupId = currentUserService.getCurrentGroupId(email);

        Rota rota = rotaRepository.findById(rotaId)
                .orElseThrow(() -> new EntityNotFoundException("Rota not found"));

        if (!rota.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Invalid group");
        }

        // --- update basic fields ---
        if (request.getName() != null) {
            rota.setName(request.getName());
        }

        if (request.getRotaDate() != null) {
            rota.setRotaDate(request.getRotaDate());
        }

        rota.setTeamId(request.getTeamId());

        // --- replace assignments ---
        rota.clearAssignments();

        for (CreateRotaAssignmentRequest item : request.getAssignments()) {

            RotaAssignment assignment = new RotaAssignment();
            assignment.setRoleName(item.getRoleName());
            assignment.setUserId(item.getUserId());
            assignment.setStatus(RotaAssignmentStatus.ASSIGNED);
            assignment.setUpdatedAt(OffsetDateTime.now());

            rota.addAssignment(assignment);
        }

        return toResponse(email, rotaRepository.save(rota));
    }
}