package com.camillus.clinic.hospital;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    @EntityGraph(attributePaths = {"department", "department.hospital"})
    List<Doctor> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"department", "department.hospital"})
    List<Doctor> findAllByDepartment_Hospital_PublicIdOrderByNameAsc(String hospitalPublicId);

    @EntityGraph(attributePaths = {"department", "department.hospital", "user"})
    Optional<Doctor> findByPublicIdIgnoreCase(String publicId);

    @EntityGraph(attributePaths = {"department", "department.hospital", "user"})
    Optional<Doctor> findByUser_PublicIdIgnoreCase(String userPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where lower(d.publicId) = lower(:publicId)")
    Optional<Doctor> findByPublicIdForUpdate(@Param("publicId") String publicId);
}
