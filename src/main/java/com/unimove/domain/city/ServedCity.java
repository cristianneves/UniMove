package com.unimove.domain.city;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Cidade em que a plataforma opera. A PK e o proprio slug normalizado
 * ({@code remanso-ba}) — e ele que e comparado com {@code users.cidade} e
 * {@code rides.cidade}, entao um id sintetico so adicionaria um join.
 *
 * Entidade privada ao pacote por contrato: outros dominios enxergam apenas
 * {@link CityCatalog}.
 */
@Entity
@Table(name = "served_cities")
@Getter
@Setter
@NoArgsConstructor
public class ServedCity {

    @Id
    @Column(name = "slug", nullable = false, updatable = false, length = 80)
    private String slug;

    /** Texto como o admin digitou ("Remanso/BA") — e o que o app exibe. */
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
