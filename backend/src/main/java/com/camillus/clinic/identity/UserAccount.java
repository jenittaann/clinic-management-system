package com.camillus.clinic.identity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36) private String publicId = UUID.randomUUID().toString();
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Role role;
    protected UserAccount() {}
    public UserAccount(String email, String passwordHash, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    public String getPublicId() { return publicId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public enum Role { PATIENT, DOCTOR, RECEPTIONIST, ADMIN }
}
