package com.unimove.domain.maps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "route_cache")
@Getter
@Setter
@NoArgsConstructor
class RouteCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "route_hash", nullable = false, unique = true, length = 128)
    private String routeHash;

    @Column(name = "distancia_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal distanciaKm;

    @Column(name = "tempo_min", nullable = false)
    private int tempoMin;

    /** Polyline codificada (precisao 5) do trajeto. Nullable para hits antigos. */
    @Column(name = "geometry", columnDefinition = "TEXT")
    private String geometry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
