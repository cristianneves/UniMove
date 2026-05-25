package com.unimove.domain.chat;

import com.unimove.domain.chat.dto.ChatMessageResponse;
import com.unimove.domain.chat.dto.SendChatMessageRequest;
import com.unimove.domain.ride.RideService;
import com.unimove.domain.user.Role;
import com.unimove.shared.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository repository;
    private final RideService rideService;
    private final ChatSseHub hub;

    public ChatService(ChatMessageRepository repository,
                       RideService rideService,
                       ChatSseHub hub) {
        this.repository = repository;
        this.rideService = rideService;
        this.hub = hub;
    }

    @Transactional
    public ChatMessageResponse send(AuthenticatedUser user, UUID rideId, SendChatMessageRequest req) {
        Role role = rideService.assertChatAllowed(user, rideId);

        ChatMessage m = new ChatMessage();
        m.setRideId(rideId);
        m.setSenderId(user.userId());
        m.setSenderRole(role);
        m.setBody(req.body().trim());
        // saveAndFlush força o INSERT pra termos o seq gerado antes de broadcast.
        ChatMessage saved = repository.saveAndFlush(m);

        ChatMessageResponse resp = ChatMessageResponse.from(saved);
        hub.broadcast(rideId, resp);
        log.info("Chat ride={} sender={} role={} seq={} len={}",
                rideId, user.userId(), role, saved.getSeq(), saved.getBody().length());
        return resp;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(AuthenticatedUser user, UUID rideId) {
        rideService.assertChatAllowed(user, rideId);
        return repository.findByRideIdOrderBySeqAsc(rideId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    /**
     * Abre o SSE. Replay opcional: se o cliente mandar Last-Event-Id (seq da
     * ultima msg que viu), reentrega tudo desde entao antes de prosseguir
     * com o stream ao vivo.
     */
    @Transactional(readOnly = true)
    public SseEmitter subscribe(AuthenticatedUser user, UUID rideId, Long lastEventId) {
        rideService.assertChatAllowed(user, rideId);
        SseEmitter emitter = hub.register(rideId);

        // Replay sincrono na propria thread — o tamanho da janela e pequeno
        // (uma corrida raramente passa de algumas dezenas de msgs).
        if (lastEventId != null) {
            List<ChatMessage> missed = repository
                    .findByRideIdAndSeqGreaterThanOrderBySeqAsc(rideId, lastEventId);
            for (ChatMessage m : missed) {
                ChatMessageResponse r = ChatMessageResponse.from(m);
                try {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(r.seq()))
                            .name("message")
                            .data(r));
                } catch (IOException ex) {
                    emitter.complete();
                    return emitter;
                }
            }
        }
        return emitter;
    }
}
