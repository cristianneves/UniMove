package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.RideStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry em memoria dos SSE emitters de STATUS da corrida, por rideId
 * (ver regra 18). Espelha o {@link com.unimove.domain.chat.ChatSseHub}, mas
 * vive no dominio ride e cobre o ciclo de vida inteiro (desde a criacao ate o
 * estado final), nao apenas a janela do chat.
 *
 * Single instance (MVP) — quando escalar horizontalmente vira pub/sub via
 * Redis ou Postgres LISTEN/NOTIFY (mesma ressalva do chat).
 */
@Component
public class RideStatusSseHub {

    private static final Logger log = LoggerFactory.getLogger(RideStatusSseHub.class);

    // 30 min de conexao — cliente reconecta e recebe o snapshot do estado atual.
    public static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByRide = new ConcurrentHashMap<>();

    public SseEmitter register(UUID rideId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByRide.computeIfAbsent(rideId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable remove = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emittersByRide.get(rideId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emittersByRide.remove(rideId, list);
                }
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ex -> remove.run());
        return emitter;
    }

    /** Builder compartilhado pra garantir mesmo id/name no broadcast e no snapshot. */
    static SseEmitter.SseEventBuilder buildEvent(RideStatusEvent event) {
        return SseEmitter.event()
                .id(String.valueOf(event.at().toEpochMilli()))
                .name("status")
                .data(event);
    }

    public void broadcast(UUID rideId, RideStatusEvent event) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByRide.get(rideId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter e : list) {
            try {
                e.send(buildEvent(event));
            } catch (IOException ex) {
                e.complete();
            }
        }
    }

    /**
     * Encerra todas as conexoes da corrida — chamado apos emitir um evento
     * terminal (COMPLETED, CANCELLED ou EXPIRED).
     */
    public void closeRide(UUID rideId) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByRide.remove(rideId);
        if (list == null) {
            return;
        }
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name("closed").data("ride-ended"));
            } catch (IOException ignored) {
                // segue para complete()
            }
            e.complete();
        }
    }

    /**
     * Heartbeat a cada 15s pra manter conexoes SSE vivas atras de proxies que
     * cortam streams idle. Comentario SSE (":") nao gera evento no cliente.
     */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        if (emittersByRide.isEmpty()) {
            return;
        }
        for (var entry : emittersByRide.entrySet()) {
            for (SseEmitter e : entry.getValue()) {
                try {
                    e.send(SseEmitter.event().comment("ping"));
                } catch (IOException ex) {
                    e.complete();
                }
            }
        }
    }

    // expose pra teste/observabilidade
    public int activeEmitters(UUID rideId) {
        List<SseEmitter> list = emittersByRide.get(rideId);
        return list == null ? 0 : list.size();
    }
}
