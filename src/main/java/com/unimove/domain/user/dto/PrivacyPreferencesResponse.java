package com.unimove.domain.user.dto;

import java.time.Instant;

/**
 * Preferencias de comunicacao e situacao do consentimento.
 * {@code transactionalNotifications} e sempre true: aviso de corrida,
 * seguranca e conta e parte do servico, nao preferencia — por isso nao ha
 * como desligar aqui.
 */
public record PrivacyPreferencesResponse(
        boolean marketingOptIn,
        Instant marketingOptInAt,
        boolean transactionalNotifications,
        String termsAcceptedVersion,
        Instant termsAcceptedAt,
        String currentTermsVersion,
        boolean termsUpToDate
) {}
