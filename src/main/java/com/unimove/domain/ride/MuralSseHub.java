package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.MuralRideRemovedEvent;
import com.unimove.domain.ride.dto.RideMuralItem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Registry em memoria dos SSE emitters do MURAL, por canal cidade+categoria.
 * Espelha o {@link RideStatusSseHub}: o motorista abre o stream, recebe um
 * snapshot da lista atual e depois eventos incrementais — "ride-added" quando
 * uma corrida entra no mural e "ride-removed" quando sai (aceite, cancelamento
 * ou expiracao). Elimina o polling do mural no app do motorista.
 *
 * Single instance (MVP) — quando escalar horizontalmente vira pub/sub via
 * Redis ou Postgres LISTEN/NOTIFY (mesma ressalva dos outros hubs).
 */
@Component
public class MuralSseHub {

    // 30 min de conexao — cliente reconecta e recebe o snapshot atual.
    public static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    public static final String REASON_ACCEPTED = "ACCEPTED";
    public static final String REASON_CANCELLED = "CANCELLED";
    public static final String REASON_EXPIRED = "EXPIRED";

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByChannel = new ConcurrentHashMap<>();

    private static String channelKey(String cidade, RideCategory category) {
        return cidade + "|" + category;
    }

    public SseEmitter register(String cidade, RideCategory category) {
        String key = channelKey(cidade, category);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByChannel.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable remove = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emittersByChannel.get(key);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emittersByChannel.remove(key, list);
                }
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ex -> remove.run());
        return emitter;
    }

    /** Snapshot inicial do stream — mesmo shape da resposta do GET /rides/mural. */
    static SseEmitter.SseEventBuilder snapshotEvent(List<RideMuralItem> items) {
        return SseEmitter.event().name("snapshot").data(items);
    }

    public void broadcastAdded(String cidade, RideCategory category, RideMuralItem item) {
        broadcast(channelKey(cidade, category),
                () -> SseEmitter.event().name("ride-added").data(item));
    }

    public void broadcastRemoved(String cidade, RideCategory category, UUID rideId, String reason) {
        MuralRideRemovedEvent event = new MuralRideRemovedEvent(rideId, reason);
        broadcast(channelKey(cidade, category),
                () -> SseEmitter.event().name("ride-removed").data(event));
    }

    /** O builder de evento nao e reaproveitavel entre emitters — um novo por send. */
    private void broadcast(String key, Supplier<SseEmitter.SseEventBuilder> event) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByChannel.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter e : list) {
            try {
                e.send(event.get());
            } catch (IOException ex) {
                e.complete();
            }
        }
    }

    /**
     * Heartbeat a cada 15s pra manter conexoes SSE vivas atras de proxies que
     * cortam streams idle. Comentario SSE (":") nao gera evento no cliente.
     */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        if (emittersByChannel.isEmpty()) {
            return;
        }
        for (var entry : emittersByChannel.entrySet()) {
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
    public int activeEmitters(String cidade, RideCategory category) {
        List<SseEmitter> list = emittersByChannel.get(channelKey(cidade, category));
        return list == null ? 0 : list.size();
    }
}
