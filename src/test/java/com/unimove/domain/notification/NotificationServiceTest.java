package com.unimove.domain.notification;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.domain.notification.dto.CampaignResponse;
import com.unimove.domain.notification.dto.CreateCampaignRequest;
import com.unimove.domain.user.MarketingAudience;
import com.unimove.domain.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @Mock NotificationRepository notifications;
    @Mock NotificationCampaignRepository campaigns;
    @Mock MarketingAudience audience;
    @Mock AdminAuditLog auditLog;

    private NotificationService service;
    private final UUID adminId = UUID.randomUUID();
    private final List<Notification> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, campaigns, audience, auditLog, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(campaigns.save(any(NotificationCampaign.class))).thenAnswer(inv -> {
            NotificationCampaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        lenient().when(notifications.saveAll(anyIterable())).thenAnswer(inv -> {
            ((Iterable<Notification>) inv.getArgument(0)).forEach(saved::add);
            return List.of();
        });
    }

    @Test
    @DisplayName("campanha vai so para o publico com opt-in do segmento (cidade normalizada) e e auditada")
    void sendToOptedInSegment() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(audience.optedInRecipients("remanso-ba", Role.PASSAGEIRO)).thenReturn(List.of(a, b));

        CampaignResponse resp = service.send(adminId,
                new CreateCampaignRequest(" Promo ", "Corrida gratis", "Remanso/BA", Role.PASSAGEIRO));

        assertThat(resp.status()).isEqualTo(CampaignStatus.SENT);
        assertThat(resp.recipients()).isEqualTo(2);
        assertThat(resp.sentAt()).isEqualTo(NOW);
        assertThat(saved).extracting(Notification::getUserId).containsExactly(a, b);
        assertThat(saved).allMatch(n -> n.getKind() == NotificationKind.PROMOTIONAL && n.getTitle().equals("Promo"));
        verify(auditLog).record(eq(adminId), eq(AuditAction.CAMPAIGN_SENT), eq("CAMPAIGN"), eq(resp.id()), anyString());
    }

    @Test
    @DisplayName("sem ninguem com opt-in: registrada como NO_AUDIENCE, nada entregue")
    void noAudience() {
        when(audience.optedInRecipients(null, null)).thenReturn(List.of());

        CampaignResponse resp = service.send(adminId, new CreateCampaignRequest("t", "b", " ", null));

        assertThat(resp.status()).isEqualTo(CampaignStatus.NO_AUDIENCE);
        assertThat(resp.recipients()).isZero();
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("previa de publico usa o mesmo filtro do envio")
    void preview() {
        when(audience.countOptedIn("remanso-ba", Role.MOTORISTA)).thenReturn(7L);

        assertThat(service.previewAudience("Remanso/BA", Role.MOTORISTA)).isEqualTo(7);
    }

    @Test
    @DisplayName("marcar lida e idempotente; de outro usuario → 404")
    void markRead() {
        UUID user = UUID.randomUUID();
        Notification n = new Notification();
        when(notifications.findByIdAndUserId(any(), eq(user))).thenReturn(Optional.of(n));

        service.markRead(user, UUID.randomUUID());
        assertThat(n.getReadAt()).isEqualTo(NOW);

        UUID other = UUID.randomUUID();
        when(notifications.findByIdAndUserId(any(), eq(other))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markRead(other, UUID.randomUUID()))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    @DisplayName("exclusao de conta apaga a caixa de entrada")
    void deleteInbox() {
        UUID user = UUID.randomUUID();
        when(notifications.deleteByUser(user)).thenReturn(3);

        assertThat(service.deleteInboxOf(user)).isEqualTo(3);
    }

    @Test
    @DisplayName("lote de entrega quebra em blocos de 500")
    void batches() {
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 1201; i++) {
            many.add(UUID.randomUUID());
        }
        when(audience.optedInRecipients(null, null)).thenReturn(many);

        service.send(adminId, new CreateCampaignRequest("t", "b", null, null));

        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notifications, times(3)).saveAll(captor.capture());
        assertThat(saved).hasSize(1201);
    }
}
