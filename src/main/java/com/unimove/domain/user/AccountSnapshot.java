package com.unimove.domain.user;

import java.time.Instant;

/** O minimo que o filtro JWT precisa saber da conta a cada request (uma consulta por PK). */
public record AccountSnapshot(UserStatus status, Instant tokensValidAfter, boolean mustChangePassword) {}
