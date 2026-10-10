package com.unimove.domain.user.document;

import com.unimove.shared.config.RetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Apaga do storage os arquivos de documento que ja nao servem: reprovados ou
 * substituidos por um envio mais novo, passado o prazo de retencao. O
 * registro da analise (tipo, status, motivo, revisor) fica.
 */
@Component
class DocumentRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentRetentionScheduler.class);

    private final DriverDocumentService documentService;
    private final RetentionProperties props;
    private final Clock clock;

    DocumentRetentionScheduler(DriverDocumentService documentService, RetentionProperties props, Clock clock) {
        this.documentService = documentService;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void purge() {
        if (!props.enabled()) {
            return;
        }
        Instant cutoff = clock.instant().minus(Duration.ofDays(props.rejectedDocumentDays()));
        try {
            int files = documentService.purgeObsoleteFiles(cutoff);
            if (files > 0) {
                log.info("Retencao: {} arquivo(s) de documento obsoleto(s) apagado(s) do storage", files);
            }
        } catch (RuntimeException e) {
            // Storage fora: tenta de novo amanha; nada foi marcado como apagado.
            log.warn("Retencao de documentos falhou (sera tentada no proximo ciclo): {}", e.toString());
        }
    }
}
