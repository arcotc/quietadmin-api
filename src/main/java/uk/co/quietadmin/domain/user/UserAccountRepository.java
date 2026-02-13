package uk.co.quietadmin.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmailAndDeletedAtIsNull(String email);

    Optional<UserAccount> findByEmailVerificationTokenAndDeletedAtIsNull(String token);

    List<UserAccount> findByIdIn(List<Long> ids);
}