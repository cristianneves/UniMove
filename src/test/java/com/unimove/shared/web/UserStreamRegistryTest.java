package com.unimove.shared.web;

import com.unimove.shared.event.AccountAccessRevokedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserStreamRegistryTest {

    private final UserStreamRegistry registry = new UserStreamRegistry();

    @Test
    @DisplayName("acesso revogado encerra todos os streams do usuario e so os dele")
    void revokeClosesOnlyThatUsersStreams() {
        UUID alvo = UUID.randomUUID();
        UUID outro = UUID.randomUUID();
        SseEmitter status = mock(SseEmitter.class);
        SseEmitter chat = mock(SseEmitter.class);
        SseEmitter alheio = mock(SseEmitter.class);
        registry.track(alvo, status);
        registry.track(alvo, chat);
        registry.track(outro, alheio);

        registry.onAccessRevoked(new AccountAccessRevokedEvent(alvo));

        verify(status).complete();
        verify(chat).complete();
        verify(alheio, never()).complete();
        assertThat(registry.openStreams(alvo)).isZero();
        assertThat(registry.openStreams(outro)).isEqualTo(1);
    }

    @Test
    @DisplayName("stream encerrado normalmente sai do indice")
    void untrackRemoves() {
        UUID user = UUID.randomUUID();
        SseEmitter e = mock(SseEmitter.class);
        registry.track(user, e);
        registry.untrack(user, e);

        registry.onAccessRevoked(new AccountAccessRevokedEvent(user));

        verify(e, never()).complete();
    }
}
