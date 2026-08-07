package com.unimove.domain.city;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimove.domain.city.dto.AdminCityItem;
import com.unimove.domain.city.dto.CityItem;
import com.unimove.domain.city.dto.OpenCityRequest;
import com.unimove.domain.user.Role;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP dos endpoints de cidade. Como no restante da suíte, os filtros
 * de segurança ficam desligados — o guard de role vive no {@code @PreAuthorize}
 * e a liberação pública de {@code /cities} no {@code SecurityConfig}, ambos
 * verificados no roteiro end-to-end.
 */
@WebMvcTest(controllers = {CityController.class, AdminCityController.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(com.unimove.shared.exception.GlobalExceptionHandler.class)
class CityControllerWebMvcTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // Um único mock serve aos dois controllers: CityCatalogService implementa
    // CityCatalog, então satisfaz também a dependência do CityController.
    @MockBean CityCatalogService cityCatalogService;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    // Exigido pelo LastSeenInterceptor, registrado no WebMvcConfig da aplicação.
    @MockBean com.unimove.domain.user.DriverService driverService;

    @BeforeEach
    void authenticateAsAdmin() {
        AuthenticatedUser admin = new AuthenticatedUser(ADMIN_ID, "admin@unimove.local",
                Role.ADMIN, "remanso-ba");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /cities devolve slug e nome de exibição")
    void listActiveCities() throws Exception {
        when(cityCatalogService.listActive()).thenReturn(List.of(new CityItem("remanso-ba", "Remanso/BA")));

        mvc.perform(get("/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("remanso-ba"))
                .andExpect(jsonPath("$[0].displayName").value("Remanso/BA"));
    }

    @Test
    @DisplayName("POST /admin/cities devolve 201 quando a cidade é nova")
    void openNewCityReturns201() throws Exception {
        when(cityCatalogService.open(eq(ADMIN_ID), any())).thenReturn(
                new CityCatalogService.OpenResult(
                        new AdminCityItem("casa-nova-ba", "Casa Nova/BA", true, 0L), true));

        mvc.perform(post("/admin/cities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new OpenCityRequest("Casa Nova/BA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("casa-nova-ba"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /admin/cities devolve 200 quando apenas reativa")
    void reopenCityReturns200() throws Exception {
        when(cityCatalogService.open(eq(ADMIN_ID), any())).thenReturn(
                new CityCatalogService.OpenResult(
                        new AdminCityItem("casa-nova-ba", "Casa Nova/BA", true, 3L), false));

        mvc.perform(post("/admin/cities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new OpenCityRequest("Casa Nova/BA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCount").value(3));
    }

    @Test
    @DisplayName("POST /admin/cities rejeita nome em branco na validação")
    void openCityRejectsBlankName() throws Exception {
        mvc.perform(post("/admin/cities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new OpenCityRequest("   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /admin/cities/{slug} devolve 204")
    void deactivateCityReturns204() throws Exception {
        mvc.perform(delete("/admin/cities/casa-nova-ba"))
                .andExpect(status().isNoContent());

        verify(cityCatalogService).deactivate(ADMIN_ID, "casa-nova-ba");
    }

    @Test
    @DisplayName("DELETE de cidade inexistente vira 404")
    void deactivateUnknownCityReturns404() throws Exception {
        doThrow(new CityNotFoundException())
                .when(cityCatalogService).deactivate(ADMIN_ID, "nao-existe");

        mvc.perform(delete("/admin/cities/nao-existe"))
                .andExpect(status().isNotFound());
    }
}
