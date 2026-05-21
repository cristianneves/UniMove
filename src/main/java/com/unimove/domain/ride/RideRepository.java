package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.AdminRideItem;
import com.unimove.domain.ride.dto.RideHistoryItem;
import com.unimove.domain.ride.dto.RideMuralItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(
            value = """
                    SELECT new com.unimove.domain.ride.dto.AdminRideItem(
                        r.id, r.cidade, r.status, r.paymentMethod, r.preco,
                        r.passageiroId, r.motoristaId,
                        r.createdAt, r.acceptedAt, r.completedAt, r.cancelledAt
                    )
                    FROM Ride r
                    """,
            countQuery = "SELECT count(r) FROM Ride r"
    )
    Page<AdminRideItem> findAllForAdmin(Pageable pageable);

    @Query(
            value = """
                    SELECT new com.unimove.domain.ride.dto.RideHistoryItem(
                        r.id, r.status, r.cidade,
                        r.latOrigem, r.lngOrigem, r.latDestino, r.lngDestino,
                        r.distanciaKm, r.tempoMin, r.preco, r.paymentMethod,
                        r.passageiroId, r.motoristaId,
                        r.createdAt, r.completedAt, r.cancelledAt
                    )
                    FROM Ride r
                    WHERE r.passageiroId = :userId
                      AND (:status IS NULL OR r.status = :status)
                    ORDER BY r.createdAt DESC
                    """,
            countQuery = """
                    SELECT count(r) FROM Ride r
                    WHERE r.passageiroId = :userId
                      AND (:status IS NULL OR r.status = :status)
                    """
    )
    Page<RideHistoryItem> findHistoryForPassenger(@Param("userId") UUID userId,
                                                  @Param("status") RideStatus status,
                                                  Pageable pageable);

    @Query(
            value = """
                    SELECT new com.unimove.domain.ride.dto.RideHistoryItem(
                        r.id, r.status, r.cidade,
                        r.latOrigem, r.lngOrigem, r.latDestino, r.lngDestino,
                        r.distanciaKm, r.tempoMin, r.preco, r.paymentMethod,
                        r.passageiroId, r.motoristaId,
                        r.createdAt, r.completedAt, r.cancelledAt
                    )
                    FROM Ride r
                    WHERE r.motoristaId = :userId
                      AND (:status IS NULL OR r.status = :status)
                    ORDER BY r.createdAt DESC
                    """,
            countQuery = """
                    SELECT count(r) FROM Ride r
                    WHERE r.motoristaId = :userId
                      AND (:status IS NULL OR r.status = :status)
                    """
    )
    Page<RideHistoryItem> findHistoryForDriver(@Param("userId") UUID userId,
                                               @Param("status") RideStatus status,
                                               Pageable pageable);
}
