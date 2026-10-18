package com.camillus.clinic.hospital;

import java.util.List;

public record DoctorScheduleResponse(String doctorId, Integer appointmentDurationMinutes,
                                    List<AvailabilityRuleResponse> recurringWeeklyRules,
                                    List<AvailableAppointmentSlotResponse> availableSlots) {}
