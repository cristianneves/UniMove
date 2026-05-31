package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.AdminRideItem;
import com.unimove.domain.ride.dto.EarningsAggregate;
import com.unimove.domain.ride.dto.RideHistoryItem;
import com.unimove.domain.ride.dto.RideMuralItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRepository extends JpaRepository<Ride, UUID> {

    Optional<Ride> findByShareToken(UUID shareToken);

    /**
     * Carrega a Ride com as paradas (stops) ja inicializadas numa unica query.
     * Usado pelo accept(), que roda FORA de transacao (a chamada ao OSRM para o
     * ETA nao pode segurar conexao/lock do banco): a entidade volta destacada,
     * entao a colecao lazy precisa vir pronta para o RideResponse.from() nao
     * estourar LazyInitializationException.
     */
    @Query("SELECT r FROM Ride r LEFT JOIN FETCH r.stops WHERE r.id = :id")
    Optional<Ride> findByIdFetchingStops(@Param("id") UUID id);

    /**
     * IDs das corridas que ja passaram do TTL no mural. Leve (so o id), apoiada
     * no indice parcial idx_rides_mural_created_at. O job de expiracao processa
     * cada id em sua propria transacao para isolar o lock otimista do aceite.
     */
    @Query("""
            SELECT r.id FROM Ride r
            WHERE r.status = com.unimove.domain.ride.RideStatus.AVAILABLE_IN_MURAL
              AND r.createdAt < :cutoff
            """)
    List<UUID> findExpirableRideIds(@Param("cutoff") Instant cutoff);

    @Query("""
            SELECT new com.unimove.domain.ride.dto.RideMuralItem(
                r.id, r.latOrigem, r.lngOrigem, r.latDestino, r.lngDestino,
                r.distanciaKm, r.tempoMin, r.preco, r.category, r.paymentMethod, r.createdAt
            )
            FROM Ride r
            WHERE r.status = com.unimove.domain.ride.RideStatus.AVAILABLE_IN_MURAL
              AND r.cidade = :cidade
              AND r.category = :category
            ORDER BY r.createdAt ASC
            """)
    List<RideMuralItem> findMural(@Param("cidade") String cidade,
                                  @Param("category") RideCategory category);

    @Query(
            value = """
                    SELECT new com.unimove.domain.ride.dto.AdminRideItem(
                        r.id, r.cidade, r.status, r.category, r.paymentMethod, r.preco, r.cancellationFee,
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
                        r.id, r.status, r.cidade, r.category,
                        r.latOrigem, r.lngOrigem, r.latDestino, r.lngDestino,
                        r.distanciaKm, r.tempoMin, r.preco, r.cancellationFee, r.paymentMethod,
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
                        r.id, r.status, r.cidade, r.category,
                        r.latOrigem, r.lngOrigem, r.latDestino, r.lngDestino,
                        r.distanciaKm, r.tempoMin, r.preco, r.cancellationFee, r.paymentMethod,
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

    @Query("""
            SELECT new com.unimove.domain.ride.dto.EarningsAggregate(
                COUNT(r), COALESCE(SUM(r.preco), 0)
            )
            FROM Ride r
            WHERE r.motoristaId = :driverId
              AND r.status = com.unimove.domain.ride.RideStatus.COMPLETED
              AND r.completedAt >= :from
              AND r.completedAt < :to
            """)
    EarningsAggregate sumDriverEarnings(@Param("driverId") UUID driverId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    interface EarningsDayRow {
        java.sql.Date getDay();
        long getRides();
        java.math.BigDecimal getGross();
    }

    interface RecentDestinationRow {
        String getAddress();
        java.math.BigDecimal getLat();
        java.math.BigDecimal getLng();
        Instant getLastUsedAt();
    }

    /**
     * Destinos recentes distintos do passageiro (tela "para onde vamos?", estilo Uber).
     * Dedup por coordenada arredondada a 4 casas (~11m, mesma precisao do route_cache,
     * regra 11) mantendo a ocorrencia mais recente; ordena por recencia e limita.
     * So considera corridas com endereco textual — coordenada solta nao vira sugestao.
     * Consulta de baixa frequencia (abertura da tela), fora do polling de 5s (regra 3).
     */
    @Query(value = """
            SELECT address, lat, lng, last_used_at FROM (
                SELECT DISTINCT ON (ROUND(lat_destino, 4), ROUND(lng_destino, 4))
                       destino_endereco AS address,
                       lat_destino      AS lat,
                       lng_destino      AS lng,
                       created_at       AS last_used_at
                FROM rides
                WHERE passageiro_id = :passageiroId
                  AND destino_endereco IS NOT NULL
                ORDER BY ROUND(lat_destino, 4), ROUND(lng_destino, 4), created_at DESC
            ) recent
            ORDER BY last_used_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RecentDestinationRow> findRecentDestinations(@Param("passageiroId") UUID passageiroId,
                                                      @Param("limit") int limit);

    @Query(value = """
            SELECT CAST(completed_at AS DATE) AS day,
                   COUNT(*) AS rides,
                   COALESCE(SUM(preco), 0) AS gross
            FROM rides
            WHERE motorista_id = :driverId
              AND status = 'COMPLETED'
              AND completed_at >= :from
              AND completed_at <  :to
            GROUP BY CAST(completed_at AS DATE)
            ORDER BY day
            """, nativeQuery = true)
    List<EarningsDayRow> findDriverEarningsByDay(@Param("driverId") UUID driverId,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to);
}
