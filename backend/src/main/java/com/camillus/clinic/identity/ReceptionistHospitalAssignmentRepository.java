package com.camillus.clinic.identity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceptionistHospitalAssignmentRepository extends JpaRepository<ReceptionistHospitalAssignment, Long> {
    boolean existsByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(String receptionistPublicId, String hospitalPublicId);
    boolean existsByReceptionist_PublicIdIgnoreCaseAndHospital_Id(String receptionistPublicId, Long hospitalId);
    void deleteByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(String receptionistPublicId, String hospitalPublicId);
}
