package com.unimove.domain.chat;

import com.unimove.domain.chat.dto.ChatMessageResponse;
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
 * Registry em memoria dos SSE emitters ativos por rideId.
 * Single instance (MVP) — quando escalar horizontalmente vira pub/sub via
 * Redis ou Postgres LISTEN/NOTIFY.
 */
@Component
public class ChatSseHub {

    private static final Logger log = LoggerFactory.getLogger(ChatSseHub.class);

    // 30 min de conexao — cliente reconecta com Last-Event-Id.
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

    public void broadcast(UUID rideId, ChatMessageResponse msg) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByRide.get(rideId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event()
                        .id(String.valueOf(msg.seq()))
                        .name("message")
                        .data(msg));
            } catch (IOException ex) {
                e.complete();
            }
        }
    }

    /**
     * Encerra todas as conexoes da corrida — chamado quando a ride entra
     * em estado final (COMPLETED ou CANCELLED).
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
     * Heartbeat a cada 15s. Proxies/load balancers costumam matar conexoes
     * SSE idle apos 30-60s, entao mandamos um comentario "ping" pra manter
     * a conexao viva. Comentarios SSE comecam com ":" e nao geram evento
     * no cliente.
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
