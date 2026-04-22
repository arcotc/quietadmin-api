package uk.co.quietadmin.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import uk.co.quietadmin.api.rota.Rota;
import uk.co.quietadmin.api.rota.RotaAssignmentRepository;
import uk.co.quietadmin.api.rota.RotaRepository;
import uk.co.quietadmin.api.rota.RotaService;
import uk.co.quietadmin.api.rota.dto.CreateRotaRequest;
import uk.co.quietadmin.domain.customer.CurrentUserService;
import uk.co.quietadmin.domain.group.Membership;
import uk.co.quietadmin.helpers.TestFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RotaServiceTest {

    @Mock RotaRepository rotaRepository;
    @Mock RotaAssignmentRepository rotaAssignmentRepository;
    @Mock CurrentUserService currentUserService;

    @InjectMocks RotaService rotaService;

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String MEMBER_EMAIL = "member@test.com";
    private static final long GROUP_ID = 1L;
    private static final long ADMIN_ID = 10L;
    private static final long MEMBER_ID = 20L;

    @BeforeEach
    void setUp() {
        when(currentUserService.getMembership(ADMIN_EMAIL))
                .thenReturn(TestFactory.adminMembership(ADMIN_ID, GROUP_ID));
        when(currentUserService.getMembership(MEMBER_EMAIL))
                .thenReturn(TestFactory.memberMembership(MEMBER_ID, GROUP_ID));
    }

    @Test
    void create_adminSucceeds() {
        doNothing().when(currentUserService).requireAdmin(ADMIN_EMAIL);
        when(currentUserService.getCurrentGroupId(ADMIN_EMAIL)).thenReturn(GROUP_ID);
        when(currentUserService.getCurrentUserId(ADMIN_EMAIL)).thenReturn(ADMIN_ID);

        CreateRotaRequest request = new CreateRotaRequest();
        request.setName("Sunday Rota");
        request.setRotaDate(LocalDate.now().plusDays(7));
        request.setAssignments(new ArrayList<>());

        Rota saved = new Rota();
        saved.setId(1L);
        saved.setName("Sunday Rota");
        saved.setGroupId(GROUP_ID);
        saved.setCreatedByUserId(ADMIN_ID);
        saved.setRotaDate(request.getRotaDate());
        when(rotaRepository.save(any(Rota.class))).thenReturn(saved);

        rotaService.create(ADMIN_EMAIL, request);

        verify(rotaRepository).save(any(Rota.class));
    }

    @Test
    void create_nonAdminIsBlocked() {
        doThrow(new AccessDeniedException("Admin access required."))
                .when(currentUserService).requireAdmin(MEMBER_EMAIL);

        CreateRotaRequest request = new CreateRotaRequest();
        request.setName("Sunday Rota");
        request.setRotaDate(LocalDate.now().plusDays(7));
        request.setAssignments(new ArrayList<>());

        assertThatThrownBy(() -> rotaService.create(MEMBER_EMAIL, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(rotaRepository, never()).save(any());
    }

    @Test
    void declineAssignment_wrongUserIsBlocked() {
        Rota rota = new Rota();
        rota.setId(1L);
        rota.setGroupId(GROUP_ID);

        when(currentUserService.getCurrentGroupId(MEMBER_EMAIL)).thenReturn(GROUP_ID);
        when(currentUserService.getCurrentUserId(MEMBER_EMAIL)).thenReturn(MEMBER_ID);
        when(rotaRepository.findById(1L)).thenReturn(Optional.of(rota));

        // Assignment belongs to a different user — returns empty
        when(rotaAssignmentRepository.findByIdAndUserId(99L, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotaService.declineAssignment(MEMBER_EMAIL, 1L, 99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_adminCanDelete() {
        doNothing().when(currentUserService).requireAdmin(ADMIN_EMAIL);
        Membership membership = TestFactory.adminMembership(ADMIN_ID, GROUP_ID);
        when(currentUserService.getMembership(ADMIN_EMAIL)).thenReturn(membership);

        Rota rota = new Rota();
        rota.setId(5L);
        rota.setGroupId(GROUP_ID);
        when(rotaRepository.findByIdAndGroupId(5L, GROUP_ID)).thenReturn(Optional.of(rota));

        rotaService.delete(ADMIN_EMAIL, 5L);

        verify(rotaRepository).delete(rota);
    }

    @Test
    void getById_crossGroupAccessDenied() {
        when(currentUserService.getCurrentGroupId(MEMBER_EMAIL)).thenReturn(GROUP_ID);

        Rota otherGroupRota = new Rota();
        otherGroupRota.setId(7L);
        otherGroupRota.setGroupId(999L); // different group
        when(rotaRepository.findById(7L)).thenReturn(Optional.of(otherGroupRota));

        assertThatThrownBy(() -> rotaService.getById(MEMBER_EMAIL, 7L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
