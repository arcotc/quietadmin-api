package uk.co.quietadmin.api.rota;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.quietadmin.api.rota.dto.*;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.domain.team.TeamRepository;
import uk.co.quietadmin.domain.user.UserAccount;
import uk.co.quietadmin.domain.user.UserAccountRepository;
import uk.co.quietadmin.security.TokenHash;
import uk.co.quietadmin.service.mail.EmailService;
import uk.co.quietadmin.service.mail.RotaShareEmailAssignment;
import uk.co.quietadmin.web.error.ApiException;
import uk.co.quietadmin.web.error.ErrorCode;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class RotaService {

    private final RotaRepository rotaRepository;
    private final RotaAssignmentRepository rotaAssignmentRepository;
    private final CurrentUserService currentUserService;
    private final RotaDeclineTokenRepository rotaDeclineTokenRepository;
    private final RotaUserDeclineTokenRepository rotaUserDeclineTokenRepository;
    private final UserAccountRepository userRepository;
    private final TeamRepository teamRepository;
    private final EmailService emailService;

    @Value("${app.mail.ui-base-url}")
    private String uiBaseUrl;

    @Value("${app.rota.decline-token-expiry-days:1}")
    private long declineTokenExpiryDays;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
            assignment.setPreferredTeamId(item.getPreferredTeamId());
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
       SHARE (ADMIN ONLY)
       ====================================================== */

    public void shareRota(String email, Long rotaId) {

        currentUserService.requireAdmin(email);

        Long groupId = currentUserService.getCurrentGroupId(email);

        Rota rota = rotaRepository.findById(rotaId)
                .orElseThrow(() -> new EntityNotFoundException("Rota not found"));

        if (!rota.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Invalid group");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = rota.getRotaDate()
                .plusDays(declineTokenExpiryDays)
                .atStartOfDay()
                .atOffset(now.getOffset());

        // Group assignments by user — one email per recipient
        Map<Long, List<RotaAssignment>> byUser = rota.getAssignments().stream()
                .filter(a -> a.getUserId() != null && a.getStatus() == RotaAssignmentStatus.ASSIGNED)
                .collect(Collectors.groupingBy(RotaAssignment::getUserId));

        for (Map.Entry<Long, List<RotaAssignment>> entry : byUser.entrySet()) {

            Long userId = entry.getKey();
            List<RotaAssignment> userAssignments = entry.getValue();

            UserAccount user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));

            // One individual decline token per assignment
            List<RotaShareEmailAssignment> emailAssignments = new ArrayList<>();
            for (RotaAssignment assignment : userAssignments) {
                String rawToken = generateToken();
                RotaDeclineToken token = new RotaDeclineToken();
                token.setAssignment(assignment);
                token.setTokenHash(TokenHash.sha256(rawToken));
                token.setExpiresAt(expiresAt);
                token.setCreatedAt(now);
                rotaDeclineTokenRepository.save(token);

                String declineLink = uiBaseUrl + "/rota/decline?token=" + rawToken;
                emailAssignments.add(new RotaShareEmailAssignment(assignment.getRoleName(), declineLink));
            }

            // One "decline all" token per user per rota
            String rawDeclineAllToken = generateToken();
            RotaUserDeclineToken declineAllToken = new RotaUserDeclineToken();
            declineAllToken.setRotaId(rotaId);
            declineAllToken.setUserId(userId);
            declineAllToken.setTokenHash(TokenHash.sha256(rawDeclineAllToken));
            declineAllToken.setExpiresAt(expiresAt);
            declineAllToken.setCreatedAt(now);
            rotaUserDeclineTokenRepository.save(declineAllToken);

            String declineAllLink = uiBaseUrl + "/rota/decline-all?token=" + rawDeclineAllToken;

            emailService.sendRotaShareEmail(
                    user.getEmail(),
                    user.getFirstName(),
                    rota.getName(),
                    rota.getRotaDate(),
                    emailAssignments,
                    declineAllLink
            );
        }
    }

    /* ======================================================
       DECLINE (PUBLIC TOKEN ACTION)
       ====================================================== */

    public void declineByToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw invalidDeclineToken();
        }

        String tokenHash = TokenHash.sha256(rawToken);
        RotaDeclineToken token = rotaDeclineTokenRepository
                .findByTokenHashAndUsedAtIsNull(tokenHash)
                .orElseThrow(this::invalidDeclineToken);

        OffsetDateTime now = OffsetDateTime.now();

        if (token.getExpiresAt().isBefore(now)) {
            throw invalidDeclineToken();
        }

        RotaAssignment assignment = token.getAssignment();

        if (assignment.getStatus() == RotaAssignmentStatus.DECLINED || assignment.getUserId() == null) {
            token.setUsedAt(now);
            rotaDeclineTokenRepository.save(token);
            return;
        }

        assignment.setUserId(null);
        assignment.setStatus(RotaAssignmentStatus.DECLINED);
        assignment.setUpdatedAt(now);
        rotaAssignmentRepository.save(assignment);

        token.setUsedAt(now);
        rotaDeclineTokenRepository.save(token);
    }

    /* ======================================================
       DECLINE ALL (PUBLIC TOKEN ACTION)
       ====================================================== */

    public void declineAllByToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw invalidDeclineToken();
        }

        String tokenHash = TokenHash.sha256(rawToken);
        RotaUserDeclineToken token = rotaUserDeclineTokenRepository
                .findByTokenHashAndUsedAtIsNull(tokenHash)
                .orElseThrow(this::invalidDeclineToken);

        OffsetDateTime now = OffsetDateTime.now();

        if (token.getExpiresAt().isBefore(now)) {
            throw invalidDeclineToken();
        }

        Rota rota = rotaRepository.findById(token.getRotaId())
                .orElseThrow(() -> new EntityNotFoundException("Rota not found"));

        for (RotaAssignment assignment : rota.getAssignments()) {
            if (token.getUserId().equals(assignment.getUserId())
                    && assignment.getStatus() == RotaAssignmentStatus.ASSIGNED) {
                assignment.setUserId(null);
                assignment.setStatus(RotaAssignmentStatus.DECLINED);
                assignment.setUpdatedAt(now);
                rotaAssignmentRepository.save(assignment);
            }
        }

        token.setUsedAt(now);
        rotaUserDeclineTokenRepository.save(token);
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

                    item.setPreferredTeamId(a.getPreferredTeamId());

                    if (a.getPreferredTeamId() != null) {
                        teamRepository.findById(a.getPreferredTeamId())
                                .ifPresent(team -> item.setPreferredTeamName(team.getName()));
                    }

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
            assignment.setPreferredTeamId(item.getPreferredTeamId());
            assignment.setStatus(RotaAssignmentStatus.ASSIGNED);
            assignment.setUpdatedAt(OffsetDateTime.now());

            rota.addAssignment(assignment);
        }

        return toResponse(email, rotaRepository.save(rota));
    }

    @Transactional
    public void delete(String username, Long rotaId) {

        Membership membership = currentUserService.getMembership(username);

        // Optional but recommended: enforce admin
        currentUserService.requireAdmin(username);

        Rota rota = rotaRepository
                .findByIdAndGroupId(rotaId, membership.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Rota not found"));

        rotaRepository.delete(rota);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiException invalidDeclineToken() {
        return new ApiException(
                400,
                ErrorCode.TOKEN_INVALID,
                "Decline link is invalid or expired"
        );
    }
}
