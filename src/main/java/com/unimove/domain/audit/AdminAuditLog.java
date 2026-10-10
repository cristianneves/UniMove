package com.unimove.domain.audit;

import java.util.UUID;

/**
 * Porta publica do dominio de auditoria. Outros dominios registram suas acoes
 * administrativas por aqui, sem conhecer a entidade nem a tabela.
 *
 * <p>Participa da transacao de quem chama: a acao e o registro sao atomicos.
 */
public interface AdminAuditLog {

    void record(UUID adminId, AuditAction action, String targetType, Object targetId, String details);
}
