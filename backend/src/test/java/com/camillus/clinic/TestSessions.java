package com.camillus.clinic;

import com.camillus.clinic.identity.AuthenticatedUser;
import com.camillus.clinic.identity.UserAccount;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

public final class TestSessions {
    private TestSessions() {}

    public static MockHttpSession forAccount(UserAccount account) {
        var principal = new AuthenticatedUser(account.getPublicId(), account.getEmail(),
                account.getPasswordHash(), account.getRole());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
