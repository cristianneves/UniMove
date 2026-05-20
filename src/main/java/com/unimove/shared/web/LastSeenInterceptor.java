package com.unimove.shared.web;

import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.Role;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LastSeenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LastSeenInterceptor.class);

    private final DriverService driverService;

    public LastSeenInterceptor(DriverService driverService) {
        this.driverService = driverService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.getPrincipal() instanceof AuthenticatedUser user
                && user.role() == Role.MOTORISTA) {
            try {
                driverService.touchLastSeenAt(user.userId());
            } catch (Exception ex) {
                log.warn("Falha ao atualizar last_seen_at do motorista {}: {}",
                        user.userId(), ex.getMessage());
            }
        }
        return true;
    }
}
