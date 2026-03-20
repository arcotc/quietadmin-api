package uk.co.quietadmin.api.rota;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface RotaRepository extends JpaRepository<Rota, Long> {
    List<Rota> findByGroupIdOrderByRotaDateAsc(Long groupId);
    List<Rota> findByGroupIdAndRotaDateGreaterThanEqualOrderByRotaDateAsc(Long groupId, LocalDate fromDate);
}