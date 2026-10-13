package com.camillus.clinic.hospital;

import java.time.LocalTime;

public record AvailabilityRuleResponse(String availabilityId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
    public static AvailabilityRuleResponse from(AvailabilitySlot slot) {
        return new AvailabilityRuleResponse(slot.getPublicId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime());
    }
}
