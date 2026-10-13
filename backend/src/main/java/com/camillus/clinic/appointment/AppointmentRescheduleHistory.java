package com.camillus.clinic.appointment;

import com.camillus.clinic.identity.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "appointment_reschedule_history", indexes =
        @Index(name = "idx_reschedule_history_appointment_time", columnList = "appointment_id, changed_at"))
public class AppointmentRescheduleHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false) private Appointment appointment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false) private UserAccount actor;
    @Column(name = "old_starts_at", nullable = false) private Instant oldStartsAt;
    @Column(name = "old_ends_at", nullable = false) private Instant oldEndsAt;
    @Column(name = "new_starts_at", nullable = false) private Instant newStartsAt;
    @Column(name = "new_ends_at", nullable = false) private Instant newEndsAt;
    @Column(name = "changed_at", nullable = false) private Instant changedAt;

    protected AppointmentRescheduleHistory() {}

    public AppointmentRescheduleHistory(Appointment appointment, UserAccount actor,
            Instant oldStartsAt, Instant oldEndsAt, Instant newStartsAt, Instant newEndsAt, Instant changedAt) {
        this.appointment = Objects.requireNonNull(appointment);
        this.actor = Objects.requireNonNull(actor);
        this.oldStartsAt = Objects.requireNonNull(oldStartsAt);
        this.oldEndsAt = Objects.requireNonNull(oldEndsAt);
        this.newStartsAt = Objects.requireNonNull(newStartsAt);
        this.newEndsAt = Objects.requireNonNull(newEndsAt);
        this.changedAt = Objects.requireNonNull(changedAt);
    }

    public Instant getOldStartsAt() { return oldStartsAt; }
    public Instant getOldEndsAt() { return oldEndsAt; }
    public Instant getNewStartsAt() { return newStartsAt; }
    public Instant getNewEndsAt() { return newEndsAt; }
    public Instant getChangedAt() { return changedAt; }
    public UserAccount getActor() { return actor; }
}
