package com.unimove.shared.event;

import java.util.UUID;

/**
 * A conta perdeu o acesso (suspensa, excluida, senha trocada/resetada).
 * Publicado na transacao da mudanca; quem segura conexao longa (SSE) escuta
 * apos o commit e derruba as do usuario — o filtro JWT ja barra os requests
 * novos, isto cobre o stream que estava aberto.
 */
public record AccountAccessRevokedEvent(UUID userId) {}
