package com.unimove.domain.audit;

import com.unimove.domain.audit.dto.AuditEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminAuditService implements AdminAuditLog {

    private static final int MAX_DETAILS = 1000;

    private final AdminAuditRepository repository;

    public AdminAuditService(AdminAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * MANDATORY: auditoria fora da transacao da acao poderia registrar algo
     * que deu rollback (ou perder o registro de algo que aconteceu).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID adminId, AuditAction action, String targetType, Object targetId, String details) {
        AdminAuditEntry entry = new AdminAuditEntry();
        entry.setAdminId(adminId);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(String.valueOf(targetId));
        entry.setDetails(details == null || details.length() <= MAX_DETAILS
                ? details
                : details.substring(0, MAX_DETAILS));
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> search(AuditAction action, String targetId, Pageable pageable) {
        String target = targetId == null || targetId.isBlank() ? null : targetId.trim();
        return repository.search(action, target, pageable);
    }
}
