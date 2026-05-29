package com.unimove.domain.maps;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class PhotonGeocodingServiceTest {

    private static final double LAT = -20.81972;
    private static final double LNG = -49.37944;

    @Test
    void forwardRetornaSugestoes() {
        AtomicInteger calls = new AtomicInteger(0);
        WebClient client = webClientReturning(calls, """
                {"features":[
                  {"geometry":{"coordinates":[-49.37944,-20.81972]},
                   "properties":{"name":"Praca","street":"Avenida Brasil","housenumber":"1200","city":"Rio Preto","state":"SP"}},
                  {"geometry":{"coordinates":[-49.36000,-20.79500]},
                   "properties":{"street":"Rua A","city":"Rio Preto"}}
                ]}
                """);
        GeocodeCacheRepository repo = mock(GeocodeCacheRepository.class);

        PhotonGeocodingService service = new PhotonGeocodingService(client, repo);
        List<GeoPlace> results = service.search("avenida brasil 1200", null, null, 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).displayName()).isEqualTo("Avenida Brasil, 1200 — Rio Preto");
        assertThat(results.get(0).lat()).isEqualByComparingTo("-20.81972");
        assertThat(results.get(0).lng()).isEqualByComparingTo("-49.37944");
        assertThat(results.get(0).city()).isEqualTo("Rio Preto");
        assertThat(calls.get()).isEqualTo(1);
        // forward nunca cacheia
        verify(repo, never()).save(any());
    }

    @Test
    void forwardComBiasEnviaLatLon() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        ExchangeFunction exchange = req -> {
            capturedQuery.set(req.url().getQuery());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"features\":[]}")
                    .build());
        };
        WebClient client = WebClient.builder().baseUrl("http://photon.test").exchangeFunction(exchange).build();
        GeocodeCacheRepository repo = mock(GeocodeCacheRepository.class);

        PhotonGeocodingService service = new PhotonGeocodingService(client, repo);
        service.search("centro", LAT, LNG, 7);

        String query = capturedQuery.get();
        assertThat(query).contains("lat=").contains("lon=").contains("limit=7");
    }

    @Test
    void forwardQueryVaziaNaoChamaPhoton() {
        AtomicInteger calls = new AtomicInteger(0);
        WebClient client = webClientReturning(calls, "{\"features\":[]}");
        GeocodeCacheRepository repo = mock(GeocodeCacheRepository.class);

        PhotonGeocodingService service = new PhotonGeocodingService(client, repo);

        assertThat(service.search("   ", null, null, 5)).isEmpty();
        assertThat(calls.get()).isZero();
    }

    @Test
    void reverseCacheMissChamaPhotonEPersiste() {
        AtomicInteger calls = new AtomicInteger(0);
        WebClient client = webClientReturning(calls, """
                {"features":[
                  {"geometry":{"coordinates":[-49.37944,-20.81972]},
                   "properties":{"street":"Avenida Brasil","housenumber":"1200","city":"Centro"}}
                ]}
                """);
        GeocodeCacheRepository repo = mock(GeocodeCacheRepository.class);
        when(repo.findByCoordHash(any())).thenReturn(Optional.empty());

        PhotonGeocodingService service = new PhotonGeocodingService(client, repo);
        GeoPlace place = service.reverse(LAT, LNG);

        assertThat(place.displayName()).isEqualTo("Avenida Brasil, 1200 — Centro");
        assertThat(place.lat()).isEqualByComparingTo("-20.81972");
        assertThat(calls.get()).isEqualTo(1);

        ArgumentCaptor<GeocodeCache> saved = ArgumentCaptor.forClass(GeocodeCache.class);
        verify(repo, times(1)).save(saved.capture());
        assertThat(saved.getValue().getCoordHash()).isEqualTo("-20.8197,-49.3794");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Avenida Brasil, 1200 — Centro");
    }

    @Test
    void reverseCacheHitNaoChamaPhoton() {
        AtomicInteger calls = new AtomicInteger(0);
        WebClient client = webClientReturning(calls, "{\"features\":[]}");

        GeocodeCacheRepository repo = mock(GeocodeCacheRepository.class);
        GeocodeCache cached = new GeocodeCache();
        cached.setCoordHash("-20.8197,-49.3794");
        cached.setDisplayName("Avenida Brasil, 1200 — Centro");
        cached.setCity("Centro");
        cached.setLat(new BigDecimal("-20.8197200"));
        cached.setLng(new BigDecimal("-49.3794400"));
        when(repo.findByCoordHash("-20.8197,-49.3794")).thenReturn(Optional.of(cached));

        PhotonGeocodingService service = new PhotonGeocodingService(client, repo);
        GeoPlace place = service.reverse(LAT, LNG);

        assertThat(place.displayName()).isEqualTo("Avenida Brasil, 1200 — Centro");
        assertThat(calls.get()).isZero();
        verify(repo, never()).save(any());
    }

    @Test
    void photon5xxLancaMapsUnavailable() {
        WebClient client = webClientWithStatus(HttpStatus.INTERNAL_SERVER_ERROR, "{}");
        GeocodeCacheRepository repo = mock(GeocodeCacheRepository.class);
        when(repo.findByCoordHash(any())).thenReturn(Optional.empty());

        PhotonGeocodingService service = new PhotonGeocodingService(client, repo);

        assertThatThrownBy(() -> service.reverse(LAT, LNG))
                .isInstanceOf(MapsUnavailableException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void reverseSemFeaturesLancaMapsUnavailable() {
        WebClient client = webClientWithStatus(HttpStatus.OK, "{\"features\":[]}");
        GeocodeCacheRepository repo = mock(GeocodeCacheRepository.class);
        when(repo.findByCoordHash(any())).thenReturn(Optional.empty());

        PhotonGeocodingService service = new PhotonGeocodingService(client, repo);

        assertThatThrownBy(() -> service.reverse(LAT, LNG))
                .isInstanceOf(MapsUnavailableException.class);
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
        return WebClient.builder().baseUrl("http://photon.test").exchangeFunction(exchange).build();
    }

    private static WebClient webClientWithStatus(HttpStatus status, String body) {
        ExchangeFunction exchange = req -> Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
        return WebClient.builder().baseUrl("http://photon.test").exchangeFunction(exchange).build();
    }
}
