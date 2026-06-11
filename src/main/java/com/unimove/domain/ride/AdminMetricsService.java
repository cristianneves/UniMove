package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.AdminMetricsResponse;
import com.unimove.domain.ride.dto.MetricsDayItem;
import com.unimove.domain.ride.dto.RideMetricsAggregate;
import com.unimove.domain.user.UserStatsService;
import com.unimove.domain.user.dto.UserStatsSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Métricas do painel admin (GET /admin/metrics). Agrega corridas e receita no
 * período por created_at, soma a fotografia atual da base de usuários (via
 * {@link UserStatsService}, sem importar entidades do domain.user) e devolve a
 * série diária para gráficos.
 */
@Service
public class AdminMetricsService {

    /** Janela default quando o admin não informa período (últimos 30 dias). */
    private static final int DEFAULT_WINDOW_DAYS = 29;

    private final RideRepository rideRepository;
    private final UserStatsService userStatsService;

    public AdminMetricsService(RideRepository rideRepository, UserStatsService userStatsService) {
        this.rideRepository = rideRepository;
        this.userStatsService = userStatsService;
    }

    @Transactional(readOnly = true)
    public AdminMetricsResponse getMetrics(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveTo = (to != null) ? to : today;
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(DEFAULT_WINDOW_DAYS);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new InvalidMetricsRangeException();
        }

        Instant fromInstant = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstantExclusive = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        RideMetricsAggregate agg = rideRepository.aggregateRideMetrics(fromInstant, toInstantExclusive);

        long total = agg == null ? 0L : agg.total();
        long completed = agg == null ? 0L : agg.completed();
        long cancelled = agg == null ? 0L : agg.cancelled();
        long expired = agg == null ? 0L : agg.expired();
        long active = total - completed - cancelled - expired;
        BigDecimal gross = (agg == null || agg.gross() == null) ? BigDecimal.ZERO : agg.gross();
        BigDecimal cancellationFees =
                (agg == null || agg.cancellationFees() == null) ? BigDecimal.ZERO : agg.cancellationFees();

        AdminMetricsResponse.RidesSummary rides = new AdminMetricsResponse.RidesSummary(
                total, completed, cancelled, expired, active,
                ratio(completed, total), ratio(cancelled, total));

        BigDecimal averageTicket = completed == 0
                ? BigDecimal.ZERO
                : gross.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP);
        AdminMetricsResponse.RevenueSummary revenue =
                new AdminMetricsResponse.RevenueSummary(gross, cancellationFees, averageTicket);

        UserStatsSnapshot u = userStatsService.snapshot();
        AdminMetricsResponse.UsersSummary users = new AdminMetricsResponse.UsersSummary(
                u.totalPassengers(), u.totalDrivers(), u.onlineDrivers(),
                u.pendingDrivers(), u.suspendedUsers());

        List<MetricsDayItem> byDay = rideRepository
                .findRideMetricsByDay(fromInstant, toInstantExclusive).stream()
                .map(row -> new MetricsDayItem(
                        row.getDay().toLocalDate(),
                        row.getTotal(),
                        row.getCompleted(),
                        row.getRevenue() == null ? BigDecimal.ZERO : row.getRevenue()))
                .toList();

        return new AdminMetricsResponse(effectiveFrom, effectiveTo, rides, revenue, users, byDay);
    }

    /** Fração [0,1] arredondada a 4 casas; 0 quando não há corridas. */
    private static double ratio(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
