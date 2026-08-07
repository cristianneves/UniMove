package com.unimove.domain.city;

import com.unimove.domain.city.dto.CityItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cities")
@Tag(name = "Cidades", description = "Cidades atendidas pela plataforma")
public class CityController {

    private final CityCatalog cityCatalog;

    public CityController(CityCatalog cityCatalog) {
        this.cityCatalog = cityCatalog;
    }

    @GetMapping
    @Operation(summary = "Cidades atendidas",
            description = "Público — o app monta o seletor de cidade do cadastro com esta lista. "
                    + "O `slug` é o valor a enviar em `cidade` no registro e no `PUT /users/me`.")
    public List<CityItem> list() {
        return cityCatalog.listActive();
    }
}
