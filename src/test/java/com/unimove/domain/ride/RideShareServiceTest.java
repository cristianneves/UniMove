package com.unimove.domain.ride;

import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.UserAccountService;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RideShareServiceTest {

    private final RideRepository rideRepository = mock(RideRepository.class);
    private final RideShareService service = new RideShareService(
            rideRepository, mock(UserAccountService.class), mock(DriverService.class));

    private final UUID passengerId = UUID.randomUUID();
    private final UUID rideId = UUID.randomUUID();
    private final UUID token = UUID.randomUUID();

    private Ride ride(RideStatus status) {
        Ride r = new Ride();
        r.setId(rideId);
        r.setPassageiroId(passengerId);
        r.setStatus(status);
        r.setShareToken(token);
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(r));
        return r;
    }

    private AuthenticatedUser user(UUID id, Role role) {
        return new AuthenticatedUser(id, "x@x.com", role, "remanso-ba");
    }

    @Test
    void ownerGetsLinkWhileRideIsAlive() {
        ride(RideStatus.DRIVER_EN_ROUTE);

        var link = service.getLink(user(passengerId, Role.PASSAGEIRO), rideId);

        assertThat(link.token()).isEqualTo(token);
        assertThat(link.path()).isEqualTo("/share/" + token);
    }

    @Test
    void otherPassengerIsDenied() {
        ride(RideStatus.IN_PROGRESS);
        assertThatThrownBy(() -> service.getLink(user(UUID.randomUUID(), Role.PASSAGEIRO), rideId))
                .isInstanceOf(RideAccessDeniedException.class);
    }

    @Test
    void driverIsDeniedEvenWithSameId() {
        ride(RideStatus.IN_PROGRESS);
        assertThatThrownBy(() -> service.getLink(user(passengerId, Role.MOTORISTA), rideId))
                .isInstanceOf(RideAccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"COMPLETED", "CANCELLED", "EXPIRED"})
    void terminalRideHasNoLink(RideStatus status) {
        ride(status);
        assertThatThrownBy(() -> service.getLink(user(passengerId, Role.PASSAGEIRO), rideId))
                .isInstanceOf(ShareLinkExpiredException.class);
    }

    @Test
    void unknownRideIsNotFound() {
        when(rideRepository.findById(rideId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getLink(user(passengerId, Role.PASSAGEIRO), rideId))
                .isInstanceOf(RideNotFoundException.class);
    }
}
