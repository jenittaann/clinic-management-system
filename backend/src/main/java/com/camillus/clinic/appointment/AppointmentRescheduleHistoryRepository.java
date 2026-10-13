package com.camillus.clinic.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentRescheduleHistoryRepository extends JpaRepository<AppointmentRescheduleHistory, Long> {
    List<AppointmentRescheduleHistory> findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(String appointmentPublicId);
}
