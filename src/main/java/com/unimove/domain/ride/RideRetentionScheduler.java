package com.unimove.domain.ride;

import com.unimove.domain.chat.ChatPrivacyPort;
import com.unimove.shared.config.RetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Expurgo por retencao das corridas antigas (LGPD). Depois do prazo a corrida
 * continua existindo para o agregado (metricas, ganhos, conciliacao), mas sem
 * o que localiza a pessoa: endereco, rota, paradas, posicao, mensagens e
 * textos livres; as coordenadas sao arredondadas a ~1 km.
 */
@Component
class RideRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RideRetentionScheduler.class);

    private final RideRepository rideRepository;
    private final RideRatingRepository ratingRepository;
    private final ChatPrivacyPort chatPrivacy;
    private final RetentionProperties props;
    private final Clock clock;

    RideRetentionScheduler(RideRepository rideRepository, RideRatingRepository ratingRepository,
                           ChatPrivacyPort chatPrivacy, RetentionProperties props, Clock clock) {
        this.rideRepository = rideRepository;
        this.ratingRepository = ratingRepository;
        this.chatPrivacy = chatPrivacy;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void purge() {
        if (!props.enabled()) {
            return;
        }
        Instant cutoff = clock.instant().atZone(ZoneOffset.UTC).minusYears(props.rideYears()).toInstant();
        int stops = rideRepository.deleteStopsOfRidesCreatedBefore(cutoff);
        int rides = rideRepository.anonymizeRidesCreatedBefore(cutoff);
        int comments = ratingRepository.clearCommentsCreatedBefore(cutoff);
        int messages = chatPrivacy.deleteOlderThan(cutoff);
        if (rides + stops + comments + messages > 0) {
            log.info("Retencao: {} corrida(s) anonimizada(s), {} parada(s), {} comentario(s), {} mensagem(ns) "
                    + "anteriores a {}", rides, stops, comments, messages, cutoff);
        }
    }
}
