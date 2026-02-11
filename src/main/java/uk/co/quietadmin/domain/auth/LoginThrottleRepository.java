package uk.co.quietadmin.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginThrottleRepository extends JpaRepository<LoginThrottle, Long> {
    Optional<LoginThrottle> findByEmailAndIpAddress(String email, String ipAddress);
}