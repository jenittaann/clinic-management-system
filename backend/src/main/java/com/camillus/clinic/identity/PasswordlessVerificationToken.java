package com.camillus.clinic.identity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "passwordless_verification_tokens")
class PasswordlessVerificationToken {
    enum Purpose { SIGN_IN, REGISTER_PATIENT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 254) private String email;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Purpose purpose;
    @Enumerated(EnumType.STRING) @Column(name = "selected_role", nullable = false, length = 32) private UserAccount.Role selectedRole;
    @Column(name = "hospital_public_id", nullable = false, length = 36) private String hospitalPublicId;
    @Column(name = "remember_me", nullable = false) private boolean rememberMe;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PasswordlessVerificationToken() {}
    PasswordlessVerificationToken(String email, String tokenHash, Purpose purpose, UserAccount.Role selectedRole,
                                  String hospitalPublicId, boolean rememberMe, Instant createdAt, Instant expiresAt) {
        this.email = email;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.selectedRole = selectedRole;
        this.hospitalPublicId = hospitalPublicId;
        this.rememberMe = rememberMe;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
    String getEmail() { return email; }
    String getTokenHash() { return tokenHash; }
    Purpose getPurpose() { return purpose; }
    UserAccount.Role getSelectedRole() { return selectedRole; }
    String getHospitalPublicId() { return hospitalPublicId; }
    boolean isRememberMe() { return rememberMe; }
    Instant getExpiresAt() { return expiresAt; }
}
