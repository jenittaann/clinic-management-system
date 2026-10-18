package com.camillus.clinic.identity;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Optional<UserAccount> findByPublicIdIgnoreCase(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where lower(u.publicId) = lower(:publicId)")
    Optional<UserAccount> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Query("select new com.camillus.clinic.identity.DoctorAccountOption(u.publicId, u.email) " +
            "from UserAccount u where u.role = :role and not exists " +
            "(select d.id from Doctor d where d.user = u) order by u.email")
    List<DoctorAccountOption> findUnlinkedAccountsByRole(@Param("role") UserAccount.Role role);
}
