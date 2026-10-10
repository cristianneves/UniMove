package com.unimove.domain.notification;

import java.util.UUID;

/** Exclusao de conta (LGPD): a caixa de entrada do titular sai junto. */
public interface NotificationPrivacyPort {

    int deleteInboxOf(UUID userId);
}
