package com.unimove.domain.user;

/**
 * Contrato publico do dominio de usuarios para consultas agregadas de outros
 * dominios. Existe para que {@code domain.city} possa mostrar ao admin quantas
 * contas ha numa cidade antes de desativa-la, sem importar a entidade
 * {@code User} — o que quebraria a regra de isolamento do CLAUDE.md.
 */
public interface UserDirectory {

    /** Quantas contas estao registradas nesta cidade (slug normalizado). */
    long countByCidade(String cidade);
}
