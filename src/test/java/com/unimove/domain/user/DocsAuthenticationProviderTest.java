package com.unimove.domain.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocsAuthenticationProviderTest {

    private final AuthService authService = mock(AuthService.class);
    private final DocsAuthenticationProvider provider = new DocsAuthenticationProvider(authService);

    private static Authentication login(String email, String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(email, password);
    }

    private static User user(Role role, boolean mustChangePassword) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("a@x.com");
        u.setRole(role);
        u.setMustChangePassword(mustChangePassword);
        return u;
    }

    @Test
    void adminIsAuthenticatedWithAdminRoleAndNoCredentials() {
        when(authService.authenticate("a@x.com", "s")).thenReturn(user(Role.ADMIN, false));

        Authentication auth = provider.authenticate(login("a@x.com", "s"));

        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void nonAdminIsRejected() {
        when(authService.authenticate("a@x.com", "s")).thenReturn(user(Role.MOTORISTA, false));
        assertThatThrownBy(() -> provider.authenticate(login("a@x.com", "s")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void adminWithTemporaryPasswordIsRejected() {
        when(authService.authenticate("a@x.com", "s")).thenReturn(user(Role.ADMIN, true));
        assertThatThrownBy(() -> provider.authenticate(login("a@x.com", "s")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void lockoutAndSuspensionBecomeSecurityExceptions() {
        when(authService.authenticate("a@x.com", "s")).thenThrow(new TooManyLoginAttemptsException(5));
        assertThatThrownBy(() -> provider.authenticate(login("a@x.com", "s")))
                .isInstanceOf(LockedException.class);

        when(authService.authenticate("b@x.com", "s")).thenThrow(new UserSuspendedException());
        assertThatThrownBy(() -> provider.authenticate(login("b@x.com", "s")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void supportsOnlyUsernamePassword() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(Authentication.class)).isFalse();
    }
}
