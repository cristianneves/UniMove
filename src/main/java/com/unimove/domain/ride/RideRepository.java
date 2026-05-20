package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.RideMuralItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RideRepository extends JpaRepository<Ride, UUID> {

    @Query("""
            SELECT new com.unimove.domain.ride.dto.RideMuralItem(
                r.id, r.latOrigem, r.lngOrigem, r.latDestino, r.lngDestino,
                r.distanciaKm, r.tempoMin, r.preco, r.paymentMethod, r.createdAt
            )
            FROM Ride r
            WHERE r.status = com.unimove.domain.ride.RideStatus.AVAILABLE_IN_MURAL
              AND r.cidade = :cidade
            ORDER BY r.createdAt ASC
            """)
    List<RideMuralItem> findMural(@Param("cidade") String cidade);
}
