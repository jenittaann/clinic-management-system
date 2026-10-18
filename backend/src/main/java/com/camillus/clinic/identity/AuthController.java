package com.camillus.clinic.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.camillus.clinic.hospital.ApiErrorResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final PasswordlessAuthService passwordless;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          PasswordlessAuthService passwordless) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.passwordless = passwordless;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken token) {
        return new CsrfTokenResponse(token.getToken());
    }

    @PostMapping("/passwordless/request")
    public ResponseEntity<?> requestPasswordless(@Valid @RequestBody PasswordlessLoginRequest request) {
        if (!passwordless.requestLoginLink(request)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiErrorResponse("Email sign-in is not configured. Contact the application administrator."));
        }
        // Keep the response identical for existing and unknown emails.
        return ResponseEntity.accepted().body(new PasswordlessRequestResponse(true));
    }

    @PostMapping("/passwordless/verify")
    public ResponseEntity<?> verifyPasswordless(@Valid @RequestBody PasswordlessVerifyRequest request,
                                                HttpServletRequest servletRequest,
                                                HttpServletResponse servletResponse) {
        PasswordlessVerificationResult result = passwordless.verify(request.token());
        if (result == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse("This sign-in link is invalid or expired. Request a new link."));
        }
        if (servletRequest.getSession(false) != null) servletRequest.changeSessionId();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                result.principal(), null, result.principal().getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return ResponseEntity.ok(new PasswordlessSessionResponse(
                IdentityResponse.from(result.principal()), result.hospitalId()));
    }

    @PostMapping("/email-sign-in")
    public ResponseEntity<?> emailSignIn(@Valid @RequestBody EmailSignInRequest request,
                                         HttpServletRequest servletRequest,
                                         HttpServletResponse servletResponse) {
        AuthenticatedUser user = passwordless.authenticateExistingAccount(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse("No linked account is available for that role and hospital."));
        }
        saveAuthenticatedSession(user, servletRequest, servletResponse);
        return ResponseEntity.ok(IdentityResponse.from(user));
    }

    @PostMapping("/patient-register")
    public ResponseEntity<?> registerPatient(@Valid @RequestBody PatientRegistrationRequest request,
                                              HttpServletRequest servletRequest,
                                              HttpServletResponse servletResponse) {
        try {
            AuthenticatedUser user = passwordless.registerOrAuthenticatePatient(request);
            saveAuthenticatedSession(user, servletRequest, servletResponse);
            return ResponseEntity.ok(IdentityResponse.from(user));
        } catch (PasswordlessAuthService.PatientRegistrationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (RegistrationService.EmailAlreadyRegisteredException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("This email is already registered. Sign in using its existing account role."));
        }
    }

    @PostMapping("/receptionist-register")
    public ResponseEntity<?> registerReceptionist(@Valid @RequestBody PatientRegistrationRequest request,
                                                   HttpServletRequest servletRequest,
                                                   HttpServletResponse servletResponse) {
        try {
            AuthenticatedUser user = passwordless.registerOrAuthenticateReceptionist(request);
            saveAuthenticatedSession(user, servletRequest, servletResponse);
            return ResponseEntity.ok(IdentityResponse.from(user));
        } catch (PasswordlessAuthService.ReceptionistRegistrationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse("Invalid email or password"));
        }

        if (!(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AuthenticationServiceException("Authenticated principal has an unsupported type");
        }
        saveAuthenticatedSession(user, servletRequest, servletResponse);

        return ResponseEntity.ok(IdentityResponse.from(user));
    }

    private void saveAuthenticatedSession(AuthenticatedUser user, HttpServletRequest servletRequest,
                                         HttpServletResponse servletResponse) {
        if (servletRequest.getSession(false) != null) servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
    }

    @GetMapping("/me")
    public IdentityResponse me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AuthenticationServiceException("Authenticated principal is unavailable");
        }
        return IdentityResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
