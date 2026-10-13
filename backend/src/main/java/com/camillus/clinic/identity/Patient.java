package com.camillus.clinic.identity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "patients")
public class Patient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, unique = true) private UserAccount user;
    private String phone;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    protected Patient() {}

    public Patient(UserAccount user, String phone, LocalDate dateOfBirth) {
        this.user = user;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
    }

    public UserAccount getUser() { return user; }
    public String getPhone() { return phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }

    public void updateProfile(String phone, LocalDate dateOfBirth) {
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
    }
}
