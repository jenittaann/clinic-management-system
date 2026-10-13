package com.camillus.clinic.hospital;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findAllByHospital_PublicIdOrderByNameAsc(String hospitalPublicId);
}
