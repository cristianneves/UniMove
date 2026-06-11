package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.AdminMetricsResponse;
import com.unimove.domain.ride.dto.RideMetricsAggregate;
import com.unimove.domain.user.UserStatsService;
import com.unimove.domain.user.dto.UserStatsSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do {@link AdminMetricsService} com Mockito.
 *
 * Cobertura: defaulting do período, derivação de "active" e das taxas, ticket
 * médio, range inválido (400) e tolerância a agregado nulo/vazio. As queries em
 * si são confiadas ao JPA (exercitadas em runtime contra Postgres real).
 */
@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

    @Mock
    RideRepository rideRepository;

    @Mock
    UserStatsService userStatsService;

    @InjectMocks
    AdminMetricsService service;

    private void stubUsers() {
        lenient().when(userStatsService.snapshot())
                .thenReturn(new UserStatsSnapshot(10, 4, 2, 1, 3));
    }

    @Test
    void derivaActiveTaxasEticketMedio() {
        stubUsers();
        // total=10, completed=6, cancelled=2, expired=1 → active=1; gross=120 → ticket=20
        when(rideRepository.aggregateRideMetrics(any(), any()))
                .thenReturn(new RideMetricsAggregate(10, 6, 2, 1,
                        new BigDecimal("120.00"), new BigDecimal("5.50")));
        when(rideRepository.findRideMetricsByDay(any(), any())).thenReturn(List.of());

        AdminMetricsResponse res = service.getMetrics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10));

        assertThat(res.rides().total()).isEqualTo(10);
        assertThat(res.rides().active()).isEqualTo(1);
        assertThat(res.rides().completionRate()).isEqualTo(0.6);
        assertThat(res.rides().cancellationRate()).isEqualTo(0.2);
        assertThat(res.revenue().gross()).isEqualByComparingTo("120.00");
        assertThat(res.revenue().cancellationFees()).isEqualByComparingTo("5.50");
        assertThat(res.revenue().averageTicket()).isEqualByComparingTo("20.00");
        assertThat(res.users().totalPassengers()).isEqualTo(10);
        assertThat(res.users().onlineDrivers()).isEqualTo(2);
        assertThat(res.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(res.to()).isEqualTo(LocalDate.of(2026, 6, 10));
    }

    @Test
    void periodoDefaultUsaUltimos30DiasAteHoje() {
        stubUsers();
        when(rideRepository.aggregateRideMetrics(any(), any()))
                .thenReturn(new RideMetricsAggregate(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO));
        when(rideRepository.findRideMetricsByDay(any(), any())).thenReturn(List.of());

        AdminMetricsResponse res = service.getMetrics(null, null);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(res.to()).isEqualTo(today);
        assertThat(res.from()).isEqualTo(today.minusDays(29));

        // 'to' vira limite exclusivo (dia seguinte ao 'to' efetivo).
        ArgumentCaptor<Instant> fromCap = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCap = ArgumentCaptor.forClass(Instant.class);
        verify(rideRepository).aggregateRideMetrics(fromCap.capture(), toCap.capture());
        assertThat(toCap.getValue())
                .isEqualTo(today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(fromCap.getValue())
                .isEqualTo(today.minusDays(29).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void rangeInvertidoLanca400() {
        assertThatThrownBy(() -> service.getMetrics(
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(InvalidMetricsRangeException.class);
    }

    @Test
    void agregadoNuloViraZeros() {
        stubUsers();
        when(rideRepository.aggregateRideMetrics(any(), any())).thenReturn(null);
        when(rideRepository.findRideMetricsByDay(any(), any())).thenReturn(List.of());

        AdminMetricsResponse res = service.getMetrics(null, null);

        assertThat(res.rides().total()).isZero();
        assertThat(res.rides().active()).isZero();
        assertThat(res.rides().completionRate()).isZero();
        assertThat(res.revenue().gross()).isEqualByComparingTo("0");
        assertThat(res.revenue().averageTicket()).isEqualByComparingTo("0");
    }
}
