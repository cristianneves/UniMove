package com.unimove.domain.maps;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, Long> {

    Optional<GeocodeCache> findByCoordHash(String coordHash);
}
