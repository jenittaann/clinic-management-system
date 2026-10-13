package com.camillus.clinic.appointment;

import com.camillus.clinic.identity.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "appointment_status_history", indexes =
        @Index(name = "idx_status_history_appointment_time", columnList = "appointment_id, changed_at"))
public class AppointmentStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false) private Appointment appointment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false) private UserAccount actor;
    @Enumerated(EnumType.STRING) @Column(name = "old_status", nullable = false, length = 32)
    private Appointment.Status oldStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 32)
    private Appointment.Status newStatus;
    @Column(name = "changed_at", nullable = false) private Instant changedAt;

    protected AppointmentStatusHistory() {}

    public AppointmentStatusHistory(Appointment appointment, UserAccount actor, Appointment.Status oldStatus,
                                    Appointment.Status newStatus, Instant changedAt) {
        this.appointment = Objects.requireNonNull(appointment);
        this.actor = Objects.requireNonNull(actor);
        this.oldStatus = Objects.requireNonNull(oldStatus);
        this.newStatus = Objects.requireNonNull(newStatus);
        this.changedAt = Objects.requireNonNull(changedAt);
    }

    public Appointment.Status getOldStatus() { return oldStatus; }
    public Appointment.Status getNewStatus() { return newStatus; }
    public Instant getChangedAt() { return changedAt; }
    public UserAccount getActor() { return actor; }
}
