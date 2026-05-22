package com.unimove.domain.ride;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rides")
@Getter
@Setter
@NoArgsConstructor
public class Ride {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "passageiro_id", nullable = false, updatable = false)
    private UUID passageiroId;

    @Column(name = "motorista_id")
    private UUID motoristaId;

    @Column(name = "cidade", nullable = false, length = 80, updatable = false)
    private String cidade;

    @Column(name = "lat_origem", nullable = false, precision = 10, scale = 7)
    private BigDecimal latOrigem;

    @Column(name = "lng_origem", nullable = false, precision = 10, scale = 7)
    private BigDecimal lngOrigem;

    @Column(name = "lat_destino", nullable = false, precision = 10, scale = 7)
    private BigDecimal latDestino;

    @Column(name = "lng_destino", nullable = false, precision = 10, scale = 7)
    private BigDecimal lngDestino;

    @Column(name = "distancia_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal distanciaKm;

    @Column(name = "tempo_min", nullable = false)
    private Integer tempoMin;

    @Column(name = "preco", nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RideCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RideStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 10)
    private PaymentMethod paymentMethod;

    @Column(name = "pix_payload", columnDefinition = "TEXT")
    private String pixPayload;

    @Column(name = "driver_current_lat", precision = 10, scale = 7)
    private BigDecimal driverCurrentLat;

    @Column(name = "driver_current_lng", precision = 10, scale = 7)
    private BigDecimal driverCurrentLng;

    @Column(name = "driver_location_updated_at")
    private Instant driverLocationUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    private CancelledBy cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "cancellation_fee", precision = 10, scale = 2)
    private BigDecimal cancellationFee;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
