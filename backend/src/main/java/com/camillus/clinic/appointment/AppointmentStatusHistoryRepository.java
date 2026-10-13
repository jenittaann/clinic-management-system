package com.camillus.clinic.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {
    List<AppointmentStatusHistory> findAllByAppointment_PublicIdIgnoreCaseOrderByChangedAtAsc(String appointmentPublicId);
}
