package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.RideHistoryItem;

import java.util.List;
import java.util.UUID;

/**
 * O que o dominio de corridas oferece aos direitos do titular (LGPD), sem
 * expor entidades: saber se ha corrida em curso, exportar as corridas e
 * apagar o texto livre que a pessoa escreveu em avaliacoes.
 */
public interface RidePrivacyPort {

    /** Corrida em andamento como passageiro ou motorista — bloqueia a exclusao da conta. */
    boolean hasActiveRide(UUID userId);

    /** Todas as corridas do usuario, como passageiro ou motorista, mais recentes primeiro. */
    List<RideHistoryItem> exportRides(UUID userId);

    /** Remove o comentario das avaliacoes que o usuario escreveu (a nota fica: compoe a media do outro). */
    int eraseRatingComments(UUID userId);
}
