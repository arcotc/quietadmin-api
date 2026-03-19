package uk.co.quietadmin.domain.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByGroupIdAndDeletedAtIsNullOrderByNameAsc(Long groupId);

    Optional<Team> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByGroupIdAndNameIgnoreCaseAndDeletedAtIsNull(Long groupId, String name);

    Optional<Team> findByIdAndGroupIdAndDeletedAtIsNull(Long id, Long groupId);

    long countById(Integer id);
}