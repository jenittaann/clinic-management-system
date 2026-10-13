package com.camillus.clinic.hospital;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    Optional<Hospital> findByPublicId(String publicId);

    @EntityGraph(attributePaths = "departments")
    List<Hospital> findAllByOrderByNameAsc();
}
