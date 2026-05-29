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

/**
 * Cache de reverse geocoding (pin → endereco). Chave = lat/lng arredondados a 4 casas
 * (~11m), suficiente pra reuso de pontos proximos. Mesma filosofia do route_cache
 * (regra 11): sem TTL no MVP.
 */
@Entity
@Table(name = "geocode_cache")
@Getter
@Setter
@NoArgsConstructor
class GeocodeCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "coord_hash", nullable = false, unique = true, length = 64)
    private String coordHash;

    @Column(name = "display_name", nullable = false, columnDefinition = "TEXT")
    private String displayName;

    @Column(name = "street", columnDefinition = "TEXT")
    private String street;

    @Column(name = "city", columnDefinition = "TEXT")
    private String city;

    @Column(name = "state", columnDefinition = "TEXT")
    private String state;

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
