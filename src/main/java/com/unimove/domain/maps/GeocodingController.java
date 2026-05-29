package com.unimove.domain.maps;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Busca de endereco (geocoding) para o app definir origem/destino/paradas:
 * digitar e achar (forward) ou arrastar o pin (reverse). Ver regra 20 / Photon.
 */
@RestController
@RequestMapping("/maps")
@Validated
@PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
public class GeocodingController {

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    /** Autocomplete: digita o endereco, recebe sugestoes. lat/lng opcionais enviesam o resultado. */
    @GetMapping("/geocode")
    public List<GeoPlace> geocode(
            @RequestParam @NotBlank @Size(min = 3, max = 200) String q,
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit) {
        return geocodingService.search(q, lat, lng, limit);
    }

    /** Reverse: pin arrastado no mapa → endereco mais proximo. */
    @GetMapping("/reverse")
    public GeoPlace reverse(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng) {
        return geocodingService.reverse(lat, lng);
    }
}
