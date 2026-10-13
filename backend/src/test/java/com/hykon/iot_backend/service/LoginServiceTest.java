
package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private LoginService loginService;

    @Test
    void refreshRejectsInactiveUserWithoutIssuingNewTokens() {

        // Arrange: create an inactive user and their refresh token.
        User inactiveUser = new User();
        inactiveUser.setEmail("inactive.test@example.com");
        inactiveUser.setStatus("INACTIVE");

        RefreshToken oldRefreshToken = new RefreshToken();
        oldRefreshToken.setToken("test-refresh-token");
        oldRefreshToken.setUser(inactiveUser);

        when(refreshTokenService.findByToken("test-refresh-token"))
                .thenReturn(oldRefreshToken);

        when(refreshTokenService.isTokenValid(oldRefreshToken))
                .thenReturn(true);

        // Act and assert: refreshing must be rejected.
        InvalidTokenException exception = assertThrows(
                InvalidTokenException.class,
                () -> loginService.refresh("test-refresh-token")
        );

        assertEquals(
                "The user account is inactive",
                exception.getMessage()
        );

        // Verify that no replacement tokens are issued.
        verify(jwtService, never())
                .generateAccessToken(any(User.class));

        verify(refreshTokenService, never())
                .rotateRefreshToken(any(RefreshToken.class));
    }
}

