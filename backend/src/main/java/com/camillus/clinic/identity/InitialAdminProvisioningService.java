package com.camillus.clinic.identity;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InitialAdminProvisioningService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    InitialAdminProvisioningService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    void provision(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.length() < 16) {
            throw new IllegalStateException("Initial admin setup requires an email and a password of at least 16 characters");
        }
        if (users.count() != 0) {
            throw new IllegalStateException("Initial admin setup is allowed only while the users table is empty");
        }
        users.saveAndFlush(new UserAccount(email.trim(), passwordEncoder.encode(password), UserAccount.Role.ADMIN));
    }
}
