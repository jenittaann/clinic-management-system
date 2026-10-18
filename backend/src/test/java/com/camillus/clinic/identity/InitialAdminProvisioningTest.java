package com.camillus.clinic.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InitialAdminProvisioningTest {
    @Autowired InitialAdminProvisioningService provisioning;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void provisionsHashedAdminOnlyForAnEmptyUsersTable() {
        String password = "a-long-admin-password-2026";

        provisioning.provision(" initial-admin@example.test ", password);

        UserAccount account = users.findByEmailIgnoreCase("initial-admin@example.test").orElseThrow();
        assertEquals(UserAccount.Role.ADMIN, account.getRole());
        assertNotEquals(password, account.getPasswordHash());
        assertTrue(passwordEncoder.matches(password, account.getPasswordHash()));
    }

    @Test
    void refusesProvisioningWhenAnyAccountAlreadyExists() {
        users.saveAndFlush(new UserAccount("existing@example.test", "already-hashed", UserAccount.Role.PATIENT));

        assertThrows(IllegalStateException.class,
                () -> provisioning.provision("admin@example.test", "a-long-admin-password-2026"));
        assertTrue(users.findByEmailIgnoreCase("admin@example.test").isEmpty());
    }

    @Test
    void rejectsWeakOrMissingBootstrapCredentials() {
        assertThrows(IllegalStateException.class, () -> provisioning.provision("admin@example.test", "short"));
        assertThrows(IllegalStateException.class, () -> provisioning.provision(" ", "a-long-admin-password-2026"));
    }
}
