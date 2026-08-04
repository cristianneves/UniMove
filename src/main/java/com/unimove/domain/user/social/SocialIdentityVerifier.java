package com.unimove.domain.user.social;

/**
 * Porta do dominio para a validacao do token de identidade emitido por um
 * provedor social. A implementacao valida a assinatura e devolve os claims;
 * quem chama nunca toca em JWT de terceiro.
 */
public interface SocialIdentityVerifier {

    /** Provedor que esta implementacao atende. */
    SocialProvider provider();

    /**
     * @throws InvalidSocialTokenException se a assinatura, o emissor, a
     *         audiencia ou a validade nao conferirem.
     */
    SocialIdentity verify(String idToken);
}
