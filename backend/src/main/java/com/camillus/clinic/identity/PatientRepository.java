package com.camillus.clinic.identity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<Patient> findByUser_PublicIdIgnoreCase(String userPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("select p from Patient p where lower(p.user.publicId) = lower(:userPublicId)")
    Optional<Patient> findByUserPublicIdForUpdate(@Param("userPublicId") String userPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("select p from Patient p where p.id = :patientId")
    Optional<Patient> findByIdForUpdate(@Param("patientId") Long patientId);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<Patient> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query(value = "select p from Patient p where exists (" +
            "select a.id from Appointment a where a.patient = p " +
            "and a.doctor.department.hospital.id in (" +
            "select assignment.hospital.id from ReceptionistHospitalAssignment assignment " +
            "where lower(assignment.receptionist.publicId) = lower(:receptionistPublicId)))",
            countQuery = "select count(p) from Patient p where exists (" +
                    "select a.id from Appointment a where a.patient = p " +
                    "and a.doctor.department.hospital.id in (" +
                    "select assignment.hospital.id from ReceptionistHospitalAssignment assignment " +
                    "where lower(assignment.receptionist.publicId) = lower(:receptionistPublicId)))")
    Page<Patient> findAllVisibleToReceptionist(@Param("receptionistPublicId") String receptionistPublicId,
                                                Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("select p from Patient p where lower(p.user.publicId) = lower(:patientPublicId) and exists (" +
            "select a.id from Appointment a where a.patient = p " +
            "and a.doctor.department.hospital.id in (" +
            "select assignment.hospital.id from ReceptionistHospitalAssignment assignment " +
            "where lower(assignment.receptionist.publicId) = lower(:receptionistPublicId)))")
    Optional<Patient> findVisibleToReceptionist(@Param("patientPublicId") String patientPublicId,
                                                 @Param("receptionistPublicId") String receptionistPublicId);

    @EntityGraph(attributePaths = "user")
    @Query("select distinct p from Patient p join p.user u where exists (" +
            "select a.id from Appointment a where a.patient = p " +
            "and lower(a.doctor.publicId) = lower(:doctorPublicId)) order by u.email")
    java.util.List<Patient> findAllForDoctor(@Param("doctorPublicId") String doctorPublicId, Pageable pageable);
}
