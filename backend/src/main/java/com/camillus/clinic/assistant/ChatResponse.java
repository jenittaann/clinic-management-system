package com.camillus.clinic.assistant;

import com.camillus.clinic.identity.UserAccount;

public record ChatResponse(UserAccount.Role role, String reply) {}
