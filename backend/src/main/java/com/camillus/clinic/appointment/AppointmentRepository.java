package com.camillus.clinic.appointment;

import jakarta.persistence.LockModeType;
import com.camillus.clinic.identity.Patient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.doctor.id = :doctorId and a.status in :statuses " +
            "and a.startsAt < :requestedEnd and a.endsAt > :requestedStart and (:excludedId is null or a.id <> :excludedId)")
    List<Appointment> findDoctorOverlapsForUpdate(@Param("doctorId") Long doctorId,
            @Param("statuses") Collection<Appointment.Status> statuses,
            @Param("requestedStart") Instant requestedStart, @Param("requestedEnd") Instant requestedEnd,
            @Param("excludedId") Long excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.patient = :patient and a.status in :statuses " +
            "and a.startsAt < :requestedEnd and a.endsAt > :requestedStart and (:excludedId is null or a.id <> :excludedId)")
    List<Appointment> findPatientOverlapsForUpdate(@Param("patient") Patient patient,
            @Param("statuses") Collection<Appointment.Status> statuses,
            @Param("requestedStart") Instant requestedStart, @Param("requestedEnd") Instant requestedEnd,
            @Param("excludedId") Long excludedId);

    @Query("select new com.camillus.clinic.appointment.AppointmentLockTarget(a.patient.id, a.doctor.publicId) " +
            "from Appointment a where lower(a.publicId) = lower(:publicId)")
    Optional<AppointmentLockTarget> findLockTargetByPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where lower(a.publicId) = lower(:publicId)")
    Optional<Appointment> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.department", "doctor.department.hospital", "doctor.user"})
    List<Appointment> findAllByPatient_User_PublicIdIgnoreCaseOrderByStartsAtAsc(String userPublicId);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.department", "doctor.department.hospital", "doctor.user"})
    Optional<Appointment> findByPublicIdIgnoreCase(String publicId);

    @Query("select coalesce(max(a.queuePosition), 0) from Appointment a where a.doctor.id = :doctorId and a.serviceDate = :serviceDate")
    Integer findMaxQueuePosition(@Param("doctorId") Long doctorId, @Param("serviceDate") LocalDate serviceDate);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor"})
    List<Appointment> findAllByDoctor_PublicIdIgnoreCaseAndServiceDateAndStatusInOrderByQueuePositionAsc(
            String doctorPublicId, LocalDate serviceDate, Collection<Appointment.Status> statuses);
}
