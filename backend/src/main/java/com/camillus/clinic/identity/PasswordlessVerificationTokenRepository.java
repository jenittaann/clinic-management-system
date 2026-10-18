package com.camillus.clinic.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

interface PasswordlessVerificationTokenRepository extends JpaRepository<PasswordlessVerificationToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordlessVerificationToken token where token.tokenHash = :hash")
    Optional<PasswordlessVerificationToken> findByTokenHashForUpdate(@Param("hash") String hash);
    void deleteByEmailIgnoreCase(String email);
    void deleteByExpiresAtBefore(Instant instant);
}
