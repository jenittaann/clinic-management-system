package com.camillus.clinic.identity;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

public final class AuthenticatedUser implements UserDetails, CredentialsContainer {
    private final String publicId;
    private final String email;
    private final UserAccount.Role role;
    private String passwordHash;

    public AuthenticatedUser(String publicId, String email, String passwordHash, UserAccount.Role role) {
        this.publicId = publicId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String publicId() { return publicId; }
    public String email() { return email; }
    public UserAccount.Role role() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[publicId=" + publicId + ", email=" + email + ", role=" + role
                + ", passwordHash=[REDACTED]]";
    }
}
