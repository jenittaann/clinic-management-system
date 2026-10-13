package com.camillus.clinic.identity;

public record IdentityResponse(String id, String email, UserAccount.Role role) {
    static IdentityResponse from(AuthenticatedUser user) {
        return new IdentityResponse(user.publicId(), user.email(), user.role());
    }
}
