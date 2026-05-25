package com.unimove.domain.ride;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pricing_configs")
@Getter
@Setter
@NoArgsConstructor
public class PricingConfig {

    public static final String DEFAULT_CIDADE = "_DEFAULT";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cidade", nullable = false, length = 80)
    private String cidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RideCategory category;

    @Column(name = "base", nullable = false, precision = 10, scale = 2)
    private BigDecimal base;

    @Column(name = "per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKm;

    @Column(name = "per_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal perMin;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_admin_id")
    private UUID updatedByAdminId;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
