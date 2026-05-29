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

/**
 * Geocoding via Photon (OSM). Forward (autocomplete) bate direto no Photon — queries
 * parciais nao sao cacheaveis (o app faz debounce). Reverse (pin → endereco) e cacheado
 * por coordenada arredondada, igual ao route_cache (regra 11). Erros do provedor viram
 * {@link MapsUnavailableException} (HTTP 503), ja tratado pelo GlobalExceptionHandler.
 */
@Service
class PhotonGeocodingService implements GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(PhotonGeocodingService.class);
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(6);

    private final WebClient photonWebClient;
    private final GeocodeCacheRepository cacheRepository;

    PhotonGeocodingService(WebClient photonWebClient, GeocodeCacheRepository cacheRepository) {
        this.photonWebClient = photonWebClient;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public List<GeoPlace> search(String query, Double biasLat, Double biasLng, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        PhotonResponse response = call(uri -> {
            uri.path("/api")
                    .queryParam("q", query)
                    .queryParam("limit", limit);
            if (biasLat != null && biasLng != null) {
                uri.queryParam("lat", biasLat).queryParam("lon", biasLng);
            }
            return uri.build();
        });
        if (response == null || response.features() == null) {
            return List.of();
        }
        return response.features().stream()
                .map(PhotonGeocodingService::toGeoPlace)
                .filter(p -> p != null)
                .toList();
    }

    @Override
    public GeoPlace reverse(double lat, double lng) {
        String hash = coordHash(lat, lng);

        Optional<GeocodeCache> cached = cacheRepository.findByCoordHash(hash);
        if (cached.isPresent()) {
            return toGeoPlace(cached.get());
        }

        PhotonResponse response = call(uri -> uri.path("/reverse")
                .queryParam("lat", lat)
                .queryParam("lon", lng)
                .build());
        if (response == null || response.features() == null || response.features().isEmpty()) {
            throw new MapsUnavailableException("Nenhum endereco encontrado para o ponto.");
        }
        GeoPlace place = toGeoPlace(response.features().get(0));
        if (place == null) {
            throw new MapsUnavailableException("Photon retornou ponto sem coordenadas.");
        }
        persist(hash, place);
        return place;
    }

    // ---- internals --------------------------------------------------------

    private PhotonResponse call(UriFn uriFn) {
        try {
            return photonWebClient.get()
                    .uri(uriFn::build)
                    .retrieve()
                    .bodyToMono(PhotonResponse.class)
                    .block(BLOCK_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("Photon retornou status {} para geocoding", e.getStatusCode());
            throw new MapsUnavailableException("Servico de busca de endereco indisponivel.", e);
        } catch (RuntimeException e) {
            log.error("Falha ao chamar Photon", e);
            throw new MapsUnavailableException("Servico de busca de endereco indisponivel.", e);
        }
    }

    @FunctionalInterface
    private interface UriFn {
        java.net.URI build(org.springframework.web.util.UriBuilder uri);
    }

    private void persist(String hash, GeoPlace place) {
        try {
            GeocodeCache entity = new GeocodeCache();
            entity.setCoordHash(hash);
            entity.setDisplayName(place.displayName());
            entity.setStreet(place.street());
            entity.setCity(place.city());
            entity.setState(place.state());
            entity.setLat(place.lat());
            entity.setLng(place.lng());
            cacheRepository.save(entity);
        } catch (DataIntegrityViolationException race) {
            log.debug("Race no insert do geocode_cache para hash {} — outro thread venceu.", hash);
        }
    }

    private static String coordHash(double lat, double lng) {
        return round4(lat) + "," + round4(lng);
    }

    private static String round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static GeoPlace toGeoPlace(GeocodeCache c) {
        return new GeoPlace(c.getDisplayName(), c.getLat(), c.getLng(),
                c.getStreet(), c.getCity(), c.getState());
    }

    /** Converte uma feature GeoJSON do Photon num GeoPlace; null se faltar coordenada. */
    private static GeoPlace toGeoPlace(Feature f) {
        if (f == null || f.geometry() == null || f.geometry().coordinates() == null
                || f.geometry().coordinates().size() < 2) {
            return null;
        }
        // GeoJSON: [lon, lat]
        double lng = f.geometry().coordinates().get(0);
        double lat = f.geometry().coordinates().get(1);
        Properties p = f.properties() != null ? f.properties() : new Properties(
                null, null, null, null, null, null, null);
        return new GeoPlace(
                formatLabel(p),
                BigDecimal.valueOf(lat).setScale(7, RoundingMode.HALF_UP),
                BigDecimal.valueOf(lng).setScale(7, RoundingMode.HALF_UP),
                p.street(),
                localityOf(p),
                p.state());
    }

    /** Rotulo amigavel: "Rua, numero — Bairro/Cidade", com fallbacks. */
    private static String formatLabel(Properties p) {
        String head;
        if (p.street() != null && !p.street().isBlank()) {
            head = p.housenumber() != null && !p.housenumber().isBlank()
                    ? p.street() + ", " + p.housenumber()
                    : p.street();
        } else {
            head = p.name();
        }
        String locality = localityOf(p);
        if (head == null || head.isBlank()) {
            head = locality;
        } else if (locality != null && !locality.isBlank() && !head.equals(locality)) {
            head = head + " — " + locality;
        }
        return head != null ? head : "Local sem nome";
    }

    private static String localityOf(Properties p) {
        if (p.city() != null && !p.city().isBlank()) {
            return p.city();
        }
        if (p.district() != null && !p.district().isBlank()) {
            return p.district();
        }
        return p.county();
    }

    // ---- Photon GeoJSON ---------------------------------------------------

    record PhotonResponse(List<Feature> features) {}

    record Feature(Geometry geometry, Properties properties) {}

    record Geometry(List<Double> coordinates) {}

    record Properties(String name, String street, String housenumber,
                      String city, String district, String county, String state) {}
}
