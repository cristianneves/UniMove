package com.unimove.domain.ride;

import com.unimove.domain.user.DriverService;
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
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminRideController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(com.unimove.shared.exception.GlobalExceptionHandler.class)
class AdminRideControllerWebMvcTest {

    @Autowired MockMvc mvc;

    @MockBean RideService rideService;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean DriverService driverService;

    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(adminId, "a@x.com", Role.ADMIN, "remanso-ba"), null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("filtros de /admin/rides chegam tipados ao service")
    void listWithFilters() throws Exception {
        when(rideService.listAdminRides(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/admin/rides?status=IN_PROGRESS&cidade=remanso-ba&from=2026-10-01&to=2026-10-09&paymentStatus=PENDING"))
                .andExpect(status().isOk());
        verify(rideService).listAdminRides(eq(PaymentStatus.PENDING), eq(RideStatus.IN_PROGRESS), eq("remanso-ba"),
                eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 9)), any());
    }

    @Test
    @DisplayName("data invalida no filtro → 400")
    void invalidDate() throws Exception {
        mvc.perform(get("/admin/rides?from=ontem")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /admin/rides/{id} → detalhe")
    void detail() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/admin/rides/" + id)).andExpect(status().isOk());
        verify(rideService).getForAdmin(id);
    }

    @Test
    @DisplayName("cancelamento admin exige motivo → 400; com motivo → 200")
    void cancelRequiresReason() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/admin/rides/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(rideService, never()).cancelByAdmin(any(), any(), any());

        mvc.perform(post("/admin/rides/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"motorista sumiu\"}"))
                .andExpect(status().isOk());
        verify(rideService).cancelByAdmin(id, adminId, "motorista sumiu");
    }
}
