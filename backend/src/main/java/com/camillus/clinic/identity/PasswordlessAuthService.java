package com.camillus.clinic.identity;

import com.camillus.clinic.hospital.DoctorRepository;
import com.camillus.clinic.hospital.HospitalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
class PasswordlessAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final HospitalRepository hospitals;
    private final DoctorRepository doctors;
    private final ReceptionistHospitalAssignmentRepository assignments;
    private final PasswordlessVerificationTokenRepository tokens;
    private final RegistrationService registration;
    private final PatientRepository patients;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final String mailFrom;
    private final String mailHost;
    private final String frontendUrl;

    PasswordlessAuthService(UserRepository users, HospitalRepository hospitals, DoctorRepository doctors,
                            ReceptionistHospitalAssignmentRepository assignments,
                            PasswordlessVerificationTokenRepository tokens, RegistrationService registration,
                            PatientRepository patients,
                            JavaMailSender mailSender, PasswordEncoder passwordEncoder,
                            @Value("${camillus.mail.from:}") String mailFrom,
                            @Value("${spring.mail.host:}") String mailHost,
                            @Value("${camillus.frontend-url}") String frontendUrl) {
        this.users = users;
        this.hospitals = hospitals;
        this.doctors = doctors;
        this.assignments = assignments;
        this.tokens = tokens;
        this.registration = registration;
        this.patients = patients;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.mailFrom = mailFrom;
        this.mailHost = mailHost;
        this.frontendUrl = frontendUrl;
    }

    @Transactional(readOnly = true)
    AuthenticatedUser authenticateExistingAccount(EmailSignInRequest request) {
        UserAccount account = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (account == null) return null;
        UserAccount.Role selectedRole;
        try { selectedRole = UserAccount.Role.valueOf(request.role().trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { return null; }
        if (selectedRole == UserAccount.Role.ADMIN || account.getRole() != selectedRole) return null;
        String hospitalId = request.hospitalId().trim();
        if (hospitals.findByPublicId(hospitalId).isEmpty()) return null;

        boolean linked = switch (account.getRole()) {
            case PATIENT -> patients.findByUser_PublicIdIgnoreCase(account.getPublicId()).isPresent();
            case DOCTOR -> doctors.findByUser_PublicIdIgnoreCase(account.getPublicId())
                    .map(doctor -> doctor.getDepartment().getHospital().getPublicId().equalsIgnoreCase(hospitalId))
                    .orElse(false);
            case RECEPTIONIST -> assignments.existsByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(
                    account.getPublicId(), hospitalId);
            case ADMIN -> false;
        };
        if (!linked) return null;
        return new AuthenticatedUser(account.getPublicId(), account.getEmail(), account.getPasswordHash(), account.getRole());
    }

    @Transactional
    AuthenticatedUser registerOrAuthenticatePatient(PatientRegistrationRequest request) {
        if (hospitals.findByPublicId(request.hospitalId().trim()).isEmpty()) {
            throw new PatientRegistrationException("Select a valid hospital before continuing.");
        }
        String email = request.email().trim();
        UserAccount account = users.findByEmailIgnoreCase(email).orElse(null);
        if (account != null) {
            if (account.getRole() != UserAccount.Role.PATIENT) {
                throw new PatientRegistrationException(
                        "This email is already registered for a different account role. Sign in using that role.");
            }
            if (patients.findByUser_PublicIdIgnoreCase(account.getPublicId()).isEmpty()) {
                throw new PatientRegistrationException(
                        "This patient account has no linked patient profile. Contact the clinic administrator.");
            }
        } else {
            account = registration.registerPatient(new RegistrationRequest(email));
        }
        return new AuthenticatedUser(account.getPublicId(), account.getEmail(), account.getPasswordHash(), account.getRole());
    }

    @Transactional
    AuthenticatedUser registerOrAuthenticateReceptionist(PatientRegistrationRequest request) {
        var hospital = hospitals.findByPublicId(request.hospitalId().trim())
                .orElseThrow(() -> new ReceptionistRegistrationException("Select a valid hospital before continuing."));
        String email = request.email().trim();
        UserAccount account = users.findByEmailIgnoreCase(email).orElse(null);
        if (account != null) {
            if (account.getRole() != UserAccount.Role.RECEPTIONIST)
                throw new ReceptionistRegistrationException(
                        "This email is already registered for a different account role. Sign in using that role.");
            if (!assignments.existsByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(
                    account.getPublicId(), hospital.getPublicId()))
                throw new ReceptionistRegistrationException(
                        "This receptionist account is not assigned to the selected hospital. Contact an administrator.");
        } else {
            byte[] randomCredential = new byte[48];
            RANDOM.nextBytes(randomCredential);
            try {
                account = users.saveAndFlush(new UserAccount(email,
                        passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(randomCredential)),
                        UserAccount.Role.RECEPTIONIST));
                assignments.saveAndFlush(new ReceptionistHospitalAssignment(account, hospital));
            } catch (DataIntegrityViolationException exception) {
                throw new ReceptionistRegistrationException(
                        "An account already exists for this email. Try signing in again.");
            }
        }
        return new AuthenticatedUser(account.getPublicId(), account.getEmail(), account.getPasswordHash(), account.getRole());
    }

    static class PatientRegistrationException extends RuntimeException {
        PatientRegistrationException(String message) { super(message); }
    }

    static class ReceptionistRegistrationException extends RuntimeException {
        ReceptionistRegistrationException(String message) { super(message); }
    }

    @Transactional
    boolean requestLoginLink(PasswordlessLoginRequest request) {
        String email = request.email().trim();
        var hospital = hospitals.findByPublicId(request.hospitalId()).orElse(null);
        if (hospital == null) return true;
        UserAccount.Role selectedRole;
        try { selectedRole = UserAccount.Role.valueOf(request.role().trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { return true; }

        if (mailHost.isBlank() || mailFrom.isBlank() || frontendUrl.isBlank()) return false;

        boolean registering = Boolean.TRUE.equals(request.registration());
        PasswordlessVerificationToken.Purpose purpose = registering
                ? PasswordlessVerificationToken.Purpose.REGISTER_PATIENT
                : PasswordlessVerificationToken.Purpose.SIGN_IN;
        if (registering) {
            if (selectedRole != UserAccount.Role.PATIENT || users.findByEmailIgnoreCase(email).isPresent()) return true;
            selectedRole = UserAccount.Role.PATIENT;
        } else if (users.findByEmailIgnoreCase(email).isEmpty()) {
            return true;
        }

        tokens.deleteByEmailIgnoreCase(email);
        tokens.deleteByExpiresAtBefore(Instant.now());
        byte[] rawBytes = new byte[32];
        RANDOM.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
        Instant now = Instant.now();
        tokens.saveAndFlush(new PasswordlessVerificationToken(email, hash(rawToken), purpose, selectedRole,
                hospital.getPublicId(), Boolean.TRUE.equals(request.rememberMe()), now, now.plus(15, ChronoUnit.MINUTES)));

        String link = frontendUrl + "/login?hospitalId=" + hospital.getPublicId() + "&token=" + rawToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Your CAMILLUS sign-in link");
        message.setText("Use this one-time link within 15 minutes to continue to CAMILLUS:\n\n" + link
                + "\n\nIf you did not request this, you can ignore this email.");
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            tokens.deleteByEmailIgnoreCase(email);
            return false;
        }
    }

    @Transactional
    PasswordlessVerificationResult verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        PasswordlessVerificationToken token = tokens.findByTokenHashForUpdate(hash(rawToken)).orElse(null);
        if (token == null) return null;
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            tokens.delete(token);
            return null;
        }
        tokens.delete(token);

        if (hospitals.findByPublicId(token.getHospitalPublicId()).isEmpty()) return null;
        UserAccount account;
        if (token.getPurpose() == PasswordlessVerificationToken.Purpose.REGISTER_PATIENT) {
            if (token.getSelectedRole() != UserAccount.Role.PATIENT) return null;
            try { account = registration.registerPatient(new RegistrationRequest(token.getEmail())); }
            catch (RegistrationService.EmailAlreadyRegisteredException exception) { return null; }
        } else {
            account = users.findByEmailIgnoreCase(token.getEmail()).orElse(null);
            if (account == null || account.getRole() != token.getSelectedRole()) return null;
        }

        if (!accountIsAssignedToHospital(account, token.getHospitalPublicId())) return null;
        AuthenticatedUser principal = new AuthenticatedUser(account.getPublicId(), account.getEmail(),
                account.getPasswordHash(), account.getRole());
        return new PasswordlessVerificationResult(principal, token.getHospitalPublicId(), token.isRememberMe());
    }

    private boolean accountIsAssignedToHospital(UserAccount account, String hospitalPublicId) {
        return switch (account.getRole()) {
            case PATIENT, ADMIN -> true;
            case DOCTOR -> doctors.findByUser_PublicIdIgnoreCase(account.getPublicId())
                    .map(doctor -> doctor.getDepartment().getHospital().getPublicId().equalsIgnoreCase(hospitalPublicId))
                    .orElse(false);
            case RECEPTIONIST -> assignments.existsByReceptionist_PublicIdIgnoreCaseAndHospital_PublicIdIgnoreCase(
                    account.getPublicId(), hospitalPublicId);
        };
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
