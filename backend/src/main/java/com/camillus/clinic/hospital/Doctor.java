package com.camillus.clinic.hospital;

import jakarta.persistence.*;
import com.camillus.clinic.identity.UserAccount;
import java.util.UUID;

@Entity @Table(name = "doctors")
public class Doctor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36) private String publicId = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id", nullable = false) private Department department;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", unique = true) private UserAccount user;
    @Column(nullable = false, length = 200) private String name;
    private String specialization;
    @Column(name = "experience_years") private Integer experienceYears;
    @Column(name = "appointment_duration_minutes") private Integer appointmentDurationMinutes = 30;
    protected Doctor() {}

    public Doctor(Department department, String name, String specialization, Integer experienceYears) {
        this.department = department;
        this.name = name;
        this.specialization = specialization;
        this.experienceYears = experienceYears;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Department getDepartment() { return department; }
    public UserAccount getUser() { return user; }
    public String getName() { return name; }
    public String getSpecialization() { return specialization; }
    public Integer getExperienceYears() { return experienceYears; }
    public Integer getAppointmentDurationMinutes() { return appointmentDurationMinutes; }

    public void assignUser(UserAccount user) { this.user = user; }
    public void setAppointmentDurationMinutes(Integer appointmentDurationMinutes) {
        this.appointmentDurationMinutes = appointmentDurationMinutes;
    }
}
