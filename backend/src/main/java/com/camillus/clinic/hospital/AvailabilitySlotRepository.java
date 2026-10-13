package com.camillus.clinic.hospital;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {
    List<AvailabilitySlot> findAllByDoctor_PublicIdOrderByDayOfWeekAscStartTimeAsc(String doctorPublicId);
    Optional<AvailabilitySlot> findByPublicIdIgnoreCaseAndDoctor_PublicIdIgnoreCase(String publicId, String doctorPublicId);

    @Query("select count(a) > 0 from AvailabilitySlot a where a.doctor.id = :doctorId and a.dayOfWeek = :day " +
            "and a.startTime < :end and a.endTime > :start and (:excludeId is null or a.id <> :excludeId)")
    boolean hasOverlap(@Param("doctorId") Long doctorId, @Param("day") int day,
                       @Param("start") LocalTime start, @Param("end") LocalTime end,
                       @Param("excludeId") Long excludeId);
}
