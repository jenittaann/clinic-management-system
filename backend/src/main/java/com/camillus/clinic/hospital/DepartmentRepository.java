package com.camillus.clinic.hospital;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findAllByHospital_PublicIdOrderByNameAsc(String hospitalPublicId);
    boolean existsByHospital_PublicIdIgnoreCaseAndNameIgnoreCase(String hospitalPublicId, String name);

    @EntityGraph(attributePaths = "hospital")
    Optional<Department> findByPublicIdIgnoreCase(String publicId);
}
