package uk.co.quietadmin.api.rota;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RotaAssignmentRepository extends JpaRepository<RotaAssignment, Long> {
    Optional<RotaAssignment> findByIdAndUserId(Long id, Long userId);
}