package com.unimove.domain.verification.dto;

import com.unimove.domain.verification.PhoneVerificationPurpose;

/** Corpo opcional do desafio. Sem corpo ou sem {@code purpose} = {@code NEW_PHONE}. */
public record CreateChallengeRequest(PhoneVerificationPurpose purpose) {
}
