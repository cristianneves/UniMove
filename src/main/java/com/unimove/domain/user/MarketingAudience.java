package com.unimove.domain.user;

import java.util.List;
import java.util.UUID;

/**
 * Porta publica do domain.user para quem envia comunicacao promocional: so
 * devolve contas ATIVAS com opt-in promocional (LGPD — consentimento), sem
 * expor a entidade. Admin nunca e publico.
 */
public interface MarketingAudience {

    /** @param cidade slug ou null (todas); @param role PASSAGEIRO/MOTORISTA ou null (ambos). */
    List<UUID> optedInRecipients(String cidade, Role role);

    long countOptedIn(String cidade, Role role);
}
