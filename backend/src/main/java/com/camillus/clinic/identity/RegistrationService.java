package com.camillus.clinic.identity;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.Base64;

@Service
class RegistrationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final PatientRepository patients;
    private final PasswordEncoder encoder;

    RegistrationService(UserRepository users, PatientRepository patients, PasswordEncoder encoder) {
        this.users = users;
        this.patients = patients;
        this.encoder = encoder;
    }

    @Transactional
    UserAccount registerPatient(RegistrationRequest request) {
        String email = request.email().trim();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            byte[] randomCredential = new byte[48];
            RANDOM.nextBytes(randomCredential);
            UserAccount account = users.saveAndFlush(new UserAccount(email,
                    encoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(randomCredential)),
                    UserAccount.Role.PATIENT));
            patients.save(new Patient(account, null, null));
            return account;
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    static class EmailAlreadyRegisteredException extends RuntimeException {}
}
