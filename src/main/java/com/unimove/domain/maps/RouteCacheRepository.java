package com.unimove.domain.maps;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RouteCacheRepository extends JpaRepository<RouteCache, Long> {

    Optional<RouteCache> findByRouteHash(String routeHash);
}
