package com.unimove.domain.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock AdminAuditRepository repository;
    @InjectMocks AdminAuditService service;

    @Test
    @DisplayName("record persiste quem, o que e sobre quem; alvo vira texto")
    void recordPersistsEntry() {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        service.record(admin, AuditAction.USER_SUSPENDED, "USER", target, "fraude");

        ArgumentCaptor<AdminAuditEntry> captor = ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(repository).save(captor.capture());
        AdminAuditEntry e = captor.getValue();
        assertThat(e.getAdminId()).isEqualTo(admin);
        assertThat(e.getAction()).isEqualTo(AuditAction.USER_SUSPENDED);
        assertThat(e.getTargetType()).isEqualTo("USER");
        assertThat(e.getTargetId()).isEqualTo(target.toString());
        assertThat(e.getDetails()).isEqualTo("fraude");
    }

    @Test
    @DisplayName("details maior que a coluna e truncado em vez de derrubar a acao")
    void recordTruncatesDetails() {
        service.record(UUID.randomUUID(), AuditAction.PRICING_UPSERTED, "PRICING", "x", "a".repeat(1500));

        ArgumentCaptor<AdminAuditEntry> captor = ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).hasSize(1000);
    }

    @Test
    @DisplayName("search trata targetId em branco como sem filtro")
    void searchBlankTargetIsNoFilter() {
        service.search(null, "  ", Pageable.unpaged());

        verify(repository).search(isNull(), isNull(), any());
    }

    @Test
    @DisplayName("search repassa filtros com trim")
    void searchPassesFilters() {
        service.search(AuditAction.CITY_OPENED, " remanso-ba ", Pageable.unpaged());

        verify(repository).search(eq(AuditAction.CITY_OPENED), eq("remanso-ba"), any());
    }
}
