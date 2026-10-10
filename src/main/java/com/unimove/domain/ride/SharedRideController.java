package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.SharedRideResponse;
import com.unimove.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/share")
public class SharedRideController {

    private static final Logger log = LoggerFactory.getLogger(SharedRideController.class);

    private final RideShareService rideShareService;

    public SharedRideController(RideShareService rideShareService) {
        this.rideShareService = rideShareService;
    }

    /**
     * Publico por natureza (quem tem o link ve a corrida), entao cada acesso
     * fica registrado — trilha para investigar link vazado (LGPD). O token
     * nao vai inteiro para o log: so o prefixo, para correlacionar.
     */
    @GetMapping("/{token}")
    public SharedRideResponse get(@PathVariable UUID token, HttpServletRequest request) {
        log.info("Link compartilhado acessado: token={}... ip={}",
                token.toString().substring(0, 8), ClientIp.of(request));
        return rideShareService.getByToken(token);
    }
}
