package com.unimove.domain.user;

import com.unimove.domain.user.dto.UserStatsSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expõe contagens agregadas de usuários/motoristas para o painel admin.
 * Interface pública do domain.user consumida pelo domain.ride (métricas),
 * mantendo a regra de fronteira: nenhuma entidade JPA cruza o pacote.
 */
@Service
public class UserStatsService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;

    public UserStatsService(UserRepository userRepository, DriverRepository driverRepository) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
    }

    @Transactional(readOnly = true)
    public UserStatsSnapshot snapshot() {
        return new UserStatsSnapshot(
                userRepository.countByRole(Role.PASSAGEIRO),
                driverRepository.count(),
                driverRepository.countByOnlineTrue(),
                driverRepository.countByApprovedFalse(),
                userRepository.countByStatus(UserStatus.SUSPENDED)
        );
    }
}
