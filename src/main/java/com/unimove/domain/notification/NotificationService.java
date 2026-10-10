package com.unimove.domain.notification;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.domain.notification.dto.CampaignResponse;
import com.unimove.domain.notification.dto.CreateCampaignRequest;
import com.unimove.domain.notification.dto.NotificationResponse;
import com.unimove.domain.user.MarketingAudience;
import com.unimove.domain.user.Role;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Caixa de entrada in-app e campanhas administrativas (sem push no MVP).
 *
 * <p>A campanha e entregue na hora, na mesma transacao: uma linha por
 * destinatario elegivel (ativo + opt-in promocional). Para o porte do piloto
 * (milhares de usuarios) isso cabe numa transacao; acima disso vira fila.
 */
@Service
public class NotificationService implements NotificationPrivacyPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int BATCH = 500;

    private final NotificationRepository notificationRepository;
    private final NotificationCampaignRepository campaignRepository;
    private final MarketingAudience audience;
    private final AdminAuditLog auditLog;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationCampaignRepository campaignRepository,
                               MarketingAudience audience,
                               AdminAuditLog auditLog,
                               Clock clock) {
        this.notificationRepository = notificationRepository;
        this.campaignRepository = campaignRepository;
        this.audience = audience;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------------

    /** Quantos receberiam, antes de disparar. */
    @Transactional(readOnly = true)
    public long previewAudience(String cidade, Role role) {
        return audience.countOptedIn(slug(cidade), role);
    }

    @Transactional
    public CampaignResponse send(UUID adminId, CreateCampaignRequest req) {
        String cidade = slug(req.cidade());
        List<UUID> recipients = audience.optedInRecipients(cidade, req.role());

        NotificationCampaign campaign = new NotificationCampaign();
        campaign.setTitle(req.title().trim());
        campaign.setBody(req.body().trim());
        campaign.setTargetCidade(cidade);
        campaign.setTargetRole(req.role());
        campaign.setCreatedByAdminId(adminId);
        campaign.setRecipients(recipients.size());
        campaign.setStatus(recipients.isEmpty() ? CampaignStatus.NO_AUDIENCE : CampaignStatus.SENT);
        campaign.setSentAt(recipients.isEmpty() ? null : clock.instant());
        campaignRepository.save(campaign);

        List<Notification> batch = new ArrayList<>(Math.min(BATCH, recipients.size()));
        for (UUID userId : recipients) {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setCampaignId(campaign.getId());
            n.setKind(NotificationKind.PROMOTIONAL);
            n.setTitle(campaign.getTitle());
            n.setBody(campaign.getBody());
            batch.add(n);
            if (batch.size() == BATCH) {
                notificationRepository.saveAll(batch);
                batch.clear();
            }
        }
        notificationRepository.saveAll(batch);

        auditLog.record(adminId, AuditAction.CAMPAIGN_SENT, "CAMPAIGN", campaign.getId(),
                "destinatarios=" + recipients.size() + " cidade=" + cidade + " role=" + req.role());
        log.info("Campanha {} enviada pelo admin {} para {} usuario(s)", campaign.getId(), adminId, recipients.size());
        return toResponse(campaign);
    }

    @Transactional(readOnly = true)
    public Page<CampaignResponse> listCampaigns(Pageable pageable) {
        return campaignRepository.findAllByOrderByCreatedAtDesc(pageable).map(NotificationService::toResponse);
    }

    // ------------------------------------------------------------------------
    // Usuario
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<NotificationResponse> inbox(UUID userId, Pageable pageable) {
        return notificationRepository.findInbox(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /** Idempotente; notificacao de outro usuario e 404 (nao 403: nao confirma que existe). */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
        }
    }

    @Override
    @Transactional
    public int deleteInboxOf(UUID userId) {
        return notificationRepository.deleteByUser(userId);
    }

    private static String slug(String cidade) {
        return cidade == null || cidade.isBlank() ? null : CityNormalizer.normalize(cidade);
    }

    private static CampaignResponse toResponse(NotificationCampaign c) {
        return new CampaignResponse(c.getId(), c.getTitle(), c.getBody(), c.getTargetCidade(), c.getTargetRole(),
                c.getStatus(), c.getRecipients(), c.getCreatedByAdminId(), c.getCreatedAt(), c.getSentAt());
    }
}
