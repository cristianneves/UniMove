package com.unimove.domain.maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
class OsrmMapsService implements MapsService {

    private static final Logger log = LoggerFactory.getLogger(OsrmMapsService.class);
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(6);

    private final WebClient osrmWebClient;
    private final RouteCacheRepository cacheRepository;

    OsrmMapsService(WebClient osrmWebClient, RouteCacheRepository cacheRepository) {
        this.osrmWebClient = osrmWebClient;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public RouteInfo route(double latOrigem, double lngOrigem, double latDestino, double lngDestino) {
        return route(List.of(
                new GeoPoint(latOrigem, lngOrigem),
                new GeoPoint(latDestino, lngDestino)));
    }

    @Override
    public RouteInfo route(List<GeoPoint> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("Rota exige ao menos origem e destino.");
        }
        String hash = RouteHasher.hash(waypoints);

        Optional<RouteCache> cached = cacheRepository.findByRouteHash(hash);
        if (cached.isPresent() && cached.get().getGeometry() != null) {
            RouteCache r = cached.get();
            return new RouteInfo(r.getDistanciaKm(), r.getTempoMin(), r.getGeometry());
        }

        OsrmResponse response = fetchFromOsrm(waypoints);
        RouteInfo info = parse(response);
        if (cached.isPresent()) {
            // Hit parcial: rota cacheada antes desta feature, sem geometria.
            // Rebusca no OSRM (uma vez) e completa a linha do trajeto no cache.
            backfillGeometry(cached.get(), info);
        } else {
            persist(hash, info);
        }
        return info;
    }

    private OsrmResponse fetchFromOsrm(List<GeoPoint> waypoints) {
        StringBuilder coords = new StringBuilder();
        for (int i = 0; i < waypoints.size(); i++) {
            if (i > 0) {
                coords.append(';');
            }
            GeoPoint p = waypoints.get(i);
            coords.append(p.lng()).append(',').append(p.lat());
        }
        String path = "/route/v1/driving/" + coords;
        try {
            return osrmWebClient.get()
                    .uri(uri -> uri.path(path)
                            .queryParam("overview", "full")
                            .queryParam("geometries", "polyline")
                            .build())
                    .retrieve()
                    .bodyToMono(OsrmResponse.class)
                    .block(BLOCK_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("OSRM retornou status {} para rota {}", e.getStatusCode(), path);
            throw new MapsUnavailableException("Servico de mapas indisponivel no momento.", e);
        } catch (RuntimeException e) {
            log.error("Falha ao chamar OSRM em {}", path, e);
            throw new MapsUnavailableException("Servico de mapas indisponivel no momento.", e);
        }
    }

    private RouteInfo parse(OsrmResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            throw new MapsUnavailableException("OSRM retornou payload sem rotas.");
        }
        OsrmRoute first = response.routes().get(0);
        BigDecimal distanciaKm = BigDecimal.valueOf(first.distance() / 1000.0)
                .setScale(3, RoundingMode.HALF_UP);
        int tempoMin = (int) Math.round(first.duration() / 60.0);
        return new RouteInfo(distanciaKm, tempoMin, first.geometry());
    }

    private void persist(String hash, RouteInfo info) {
        try {
            RouteCache entity = new RouteCache();
            entity.setRouteHash(hash);
            entity.setDistanciaKm(info.distanciaKm());
            entity.setTempoMin(info.tempoMin());
            entity.setGeometry(info.geometry());
            cacheRepository.save(entity);
        } catch (DataIntegrityViolationException race) {
            log.debug("Race no insert do cache para hash {} — outro thread venceu.", hash);
        }
    }

    /** Completa a geometria de uma entrada de cache antiga (criada sem polyline). */
    private void backfillGeometry(RouteCache entity, RouteInfo info) {
        try {
            entity.setGeometry(info.geometry());
            cacheRepository.save(entity);
        } catch (RuntimeException e) {
            log.debug("Falha no backfill de geometria do cache {} — ignorado.", entity.getRouteHash());
        }
    }

    record OsrmResponse(List<OsrmRoute> routes) {}

    record OsrmRoute(double distance, double duration, String geometry) {}
}
