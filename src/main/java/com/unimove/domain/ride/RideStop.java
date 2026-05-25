package com.unimove.domain.ride;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Parada intermediaria de uma {@link Ride}. Mapeada como @ElementCollection na
 * tabela ride_stops, com a ordem preservada por @OrderColumn (coluna seq).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class RideStop {

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    public RideStop(BigDecimal lat, BigDecimal lng) {
        this.lat = lat;
        this.lng = lng;
    }
}
