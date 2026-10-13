package com.camillus.clinic.appointment;

import com.camillus.clinic.hospital.Doctor;
import com.camillus.clinic.identity.Patient;
import com.camillus.clinic.identity.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "appointments")
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36) private String publicId = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "patient_id", nullable = false) private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false) private Doctor doctor;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Status status;
    @Column(name = "checked_in_at") private Instant checkedInAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "checked_in_by_user_id") private UserAccount checkedInBy;
    @Column(name = "service_date") private LocalDate serviceDate;
    @Column(name = "queue_position") private Integer queuePosition;
    protected Appointment() {}

    public Appointment(Patient patient, Doctor doctor, Instant startsAt, Instant endsAt, Status status) {
        this.patient = Objects.requireNonNull(patient);
        this.doctor = Objects.requireNonNull(doctor);
        this.startsAt = Objects.requireNonNull(startsAt);
        this.endsAt = Objects.requireNonNull(endsAt);
        this.status = Objects.requireNonNull(status);
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Status getStatus() { return status; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public UserAccount getCheckedInBy() { return checkedInBy; }
    public LocalDate getServiceDate() { return serviceDate; }
    public Integer getQueuePosition() { return queuePosition; }
    public void cancel() { this.status = Status.CANCELLED; }
    public void checkIn(UserAccount actor, Instant at, LocalDate serviceDate, int queuePosition) {
        this.checkedInBy = Objects.requireNonNull(actor);
        this.checkedInAt = Objects.requireNonNull(at);
        this.serviceDate = Objects.requireNonNull(serviceDate);
        this.queuePosition = queuePosition;
        this.status = Status.WAITING;
    }
    public void transitionTo(Status status) { this.status = Objects.requireNonNull(status); }
    public void reschedule(Instant startsAt, Instant endsAt) {
        this.startsAt = Objects.requireNonNull(startsAt);
        this.endsAt = Objects.requireNonNull(endsAt);
    }
    public enum Status { REQUESTED, CONFIRMED, WAITING, IN_PROGRESS, NO_SHOW, CANCELLED, COMPLETED }
}
