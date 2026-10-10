package com.unimove.shared.web;

import com.unimove.shared.event.AccountAccessRevokedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Indice usuario → streams SSE abertos, transversal aos hubs (status, mural,
 * chat), que continuam indexados pelo que transmitem. Serve so para derrubar
 * de uma vez as conexoes de quem perdeu o acesso.
 *
 * <p>Em memoria, mesma ressalva de instancia unica dos hubs.
 */
@Component
public class UserStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(UserStreamRegistry.class);

    private final ConcurrentHashMap<UUID, Set<SseEmitter>> streamsByUser = new ConcurrentHashMap<>();

    /**
     * O hub chama ao abrir o stream e chama {@link #untrack} na propria
     * limpeza: o SseEmitter aceita um unico callback de conclusao, entao o
     * registro nao pode registrar o seu.
     */
    public void track(UUID userId, SseEmitter emitter) {
        streamsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
    }

    public void untrack(UUID userId, SseEmitter emitter) {
        streamsByUser.computeIfPresent(userId, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccessRevoked(AccountAccessRevokedEvent event) {
        Set<SseEmitter> streams = streamsByUser.remove(event.userId());
        if (streams == null || streams.isEmpty()) {
            return;
        }
        streams.forEach(SseEmitter::complete);
        log.info("{} stream(s) SSE encerrado(s) do usuario {} (acesso revogado)", streams.size(), event.userId());
    }

    int openStreams(UUID userId) {
        Set<SseEmitter> streams = streamsByUser.get(userId);
        return streams == null ? 0 : streams.size();
    }
}
