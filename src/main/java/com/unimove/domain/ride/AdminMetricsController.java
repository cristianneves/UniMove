package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.AdminMetricsResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/metrics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    public AdminMetricsController(AdminMetricsService adminMetricsService) {
        this.adminMetricsService = adminMetricsService;
    }

    @GetMapping
    @Operation(summary = "Painel de métricas do admin",
            description = "Agrega corridas e receita no período (por data de criação, default últimos 30 dias) "
                    + "e a fotografia atual da base de usuários/motoristas, mais a série diária para gráficos.")
    public AdminMetricsResponse metrics(@RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return adminMetricsService.getMetrics(from, to);
    }
}
