package com.unimove.domain.user.social;

/**
 * Identidade ja provada pelo provedor.
 *
 * @param provider      provedor que assinou o token.
 * @param subject       identificador estavel do usuario no provedor (o {@code sub}).
 *                      E a chave de vinculacao — diferente do e-mail, nunca muda.
 * @param email         e-mail da conta no provedor, normalizado em minusculas.
 * @param emailVerified se o provedor confirmou a posse do e-mail. Sem isso a
 *                      vinculacao por e-mail com uma conta existente seria um
 *                      sequestro de conta.
 * @param name          nome de exibicao, usado so para pre-preencher o cadastro.
 */
public record SocialIdentity(
        SocialProvider provider,
        String subject,
        String email,
        boolean emailVerified,
        String name
) {
}
