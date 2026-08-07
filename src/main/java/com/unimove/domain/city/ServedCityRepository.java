package com.unimove.domain.city;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ServedCityRepository extends JpaRepository<ServedCity, String> {

    boolean existsBySlugAndActiveTrue(String slug);

    List<ServedCity> findAllByActiveTrueOrderByDisplayNameAsc();

    List<ServedCity> findAllByOrderByDisplayNameAsc();
}
