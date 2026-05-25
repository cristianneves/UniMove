package com.unimove.domain.maps;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OsrmMapsServiceTest {

    private static final double LAT_O = -20.81972;
    private static final double LNG_O = -49.37944;
    private static final double LAT_D = -20.79500;
    private static final double LNG_D = -49.36000;

    @Test
    void cacheHitNaoChamaOsrm() {
        AtomicInteger osrmCalls = new AtomicInteger(0);
        WebClient client = webClientReturning(osrmCalls, """
                {"routes":[{"distance":3000,"duration":600}]}
                """);

        RouteCacheRepository repo = mock(RouteCacheRepository.class);
        RouteCache cached = new RouteCache();
        cached.setRouteHash(RouteHasher.hash(LAT_O, LNG_O, LAT_D, LNG_D));
        cached.setDistanciaKm(new BigDecimal("2.500"));
        cached.setTempoMin(7);
        when(repo.findByRouteHash(cached.getRouteHash())).thenReturn(Optional.of(cached));

        OsrmMapsService service = new OsrmMapsService(client, repo);
        RouteInfo info = service.route(LAT_O, LNG_O, LAT_D, LNG_D);

        assertThat(info.distanciaKm()).isEqualByComparingTo("2.500");
        assertThat(info.tempoMin()).isEqualTo(7);
        assertThat(osrmCalls.get()).isZero();
        verify(repo, never()).save(any());
    }

    @Test
    void cacheMissChamaOsrmEConverteUnidadesEPersiste() {
        AtomicInteger osrmCalls = new AtomicInteger(0);
        WebClient client = webClientReturning(osrmCalls, """
                {"routes":[{"distance":3500,"duration":420}]}
                """);

        RouteCacheRepository repo = mock(RouteCacheRepository.class);
        when(repo.findByRouteHash(any())).thenReturn(Optional.empty());

        OsrmMapsService service = new OsrmMapsService(client, repo);
        RouteInfo info = service.route(LAT_O, LNG_O, LAT_D, LNG_D);

        // 3500m = 3.500km; 420s = 7min
        assertThat(info.distanciaKm()).isEqualByComparingTo("3.500");
        assertThat(info.tempoMin()).isEqualTo(7);
        assertThat(osrmCalls.get()).isEqualTo(1);
        verify(repo, times(1)).save(any(RouteCache.class));
    }

    @Test
    void osrm5xxLancaMapsUnavailable() {
        WebClient client = webClientWithStatus(HttpStatus.INTERNAL_SERVER_ERROR, "{}");
        RouteCacheRepository repo = mock(RouteCacheRepository.class);
        when(repo.findByRouteHash(any())).thenReturn(Optional.empty());

        OsrmMapsService service = new OsrmMapsService(client, repo);

        assertThatThrownBy(() -> service.route(LAT_O, LNG_O, LAT_D, LNG_D))
                .isInstanceOf(MapsUnavailableException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void osrmRetornaSemRotasLancaMapsUnavailable() {
        WebClient client = webClientWithStatus(HttpStatus.OK, """
                {"routes":[]}
                """);
        RouteCacheRepository repo = mock(RouteCacheRepository.class);
        when(repo.findByRouteHash(any())).thenReturn(Optional.empty());

        OsrmMapsService service = new OsrmMapsService(client, repo);

        assertThatThrownBy(() -> service.route(LAT_O, LNG_O, LAT_D, LNG_D))
                .isInstanceOf(MapsUnavailableException.class);
    }

    @Test
    void raceNoInsertEEngolida() {
        AtomicInteger osrmCalls = new AtomicInteger(0);
        WebClient client = webClientReturning(osrmCalls, """
                {"routes":[{"distance":1000,"duration":120}]}
                """);

        RouteCacheRepository repo = mock(RouteCacheRepository.class);
        when(repo.findByRouteHash(any())).thenReturn(Optional.empty());
        when(repo.save(any(RouteCache.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate route_hash"));

        OsrmMapsService service = new OsrmMapsService(client, repo);

        // Mesmo com race no save, o resultado obtido do OSRM ainda deve ser retornado.
        RouteInfo info = service.route(LAT_O, LNG_O, LAT_D, LNG_D);
        assertThat(info.distanciaKm()).isEqualByComparingTo("1.000");
        assertThat(info.tempoMin()).isEqualTo(2);
    }

    @Test
    void rotaComParadasEnviaTodosOsWaypointsNaOrdem() {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        ExchangeFunction exchange = req -> {
            capturedPath.set(req.url().getPath());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"routes\":[{\"distance\":8000,\"duration\":1200}]}")
                    .build());
        };
        WebClient client = WebClient.builder()
                .baseUrl("http://osrm.test").exchangeFunction(exchange).build();

        RouteCacheRepository repo = mock(RouteCacheRepository.class);
        when(repo.findByRouteHash(any())).thenReturn(Optional.empty());

        OsrmMapsService service = new OsrmMapsService(client, repo);
        RouteInfo info = service.route(List.of(
                new GeoPoint(LAT_O, LNG_O),
                new GeoPoint(-20.80500, -49.37000),
                new GeoPoint(LAT_D, LNG_D)));

        assertThat(info.distanciaKm()).isEqualByComparingTo("8.000");
        assertThat(info.tempoMin()).isEqualTo(20);
        // OSRM usa lng,lat separados por ';' — 3 waypoints => 2 ';'
        String path = capturedPath.get();
        assertThat(path).startsWith("/route/v1/driving/");
        assertThat(path.chars().filter(c -> c == ';').count()).isEqualTo(2);
        assertThat(path).contains(LNG_O + "," + LAT_O);
        assertThat(path).contains(LNG_D + "," + LAT_D);
    }

    // ---- helpers ----------------------------------------------------------

    private static WebClient webClientReturning(AtomicInteger callCounter, String body) {
        ExchangeFunction exchange = req -> {
            callCounter.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        };
        return WebClient.builder().baseUrl("http://osrm.test").exchangeFunction(exchange).build();
    }

    private static WebClient webClientWithStatus(HttpStatus status, String body) {
        ExchangeFunction exchange = req -> Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
        return WebClient.builder().baseUrl("http://osrm.test").exchangeFunction(exchange).build();
    }
}
