package com.camillus.clinic.hospital;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity @Table(name = "availability_slots")
public class AvailabilitySlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36) private String publicId = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false) private Doctor doctor;
    @Column(name = "day_of_week", nullable = false) private int dayOfWeek;
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    protected AvailabilitySlot() {}

    public AvailabilitySlot(Doctor doctor, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.doctor = doctor;
        updateRule(dayOfWeek, startTime, endTime);
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Doctor getDoctor() { return doctor; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }

    public void updateRule(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
