package org.tc.mtracker.unit.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.tc.mtracker.auth.dto.ResetPasswordRequestDto;
import org.tc.mtracker.auth.dto.UpdatePasswordRequestDto;
import org.tc.mtracker.auth.mail.AuthEmailService;
import org.tc.mtracker.auth.model.RefreshToken;
import org.tc.mtracker.auth.service.PasswordManagementService;
import org.tc.mtracker.auth.service.RefreshTokenService;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.support.factory.EntityTestFactory;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;
import org.tc.mtracker.utils.exceptions.InvalidPasswordException;
import org.tc.mtracker.utils.exceptions.UserResetPasswordException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PasswordManagementServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthEmailService authEmailService;

    @InjectMocks
    private PasswordManagementService passwordManagementService;

    @Test
    void shouldResetPasswordAndReturnTokensWhenTokenPurposeIsValid() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto("NewStrongPass!1", "NewStrongPass!1");
        RefreshToken refreshToken = EntityTestFactory.refreshToken("refresh-token", user, LocalDateTime.now().plusDays(1));

        when(jwtService.extractClaim(eq("reset-token"), any())).thenReturn("password_reset");
        when(jwtService.extractUsername("reset-token")).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(dto.password())).thenReturn("encoded-password");
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

        JwtResponseDTO result = passwordManagementService.resetPassword("reset-token", dto);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectResetPasswordWhenConfirmationDoesNotMatch() {
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto("NewStrongPass!1", "Mismatch!1");

        assertThatThrownBy(() -> passwordManagementService.resetPassword("token", dto))
                .isInstanceOf(UserResetPasswordException.class);

        verifyNoInteractions(jwtService, userRepository, passwordEncoder, refreshTokenService);
    }

    @Test
    void shouldRejectResetPasswordWhenNewPasswordIsSameAsCurrentPassword() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto("OldPassword!1", "OldPassword!1");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(dto.password(), user.getPassword())).thenReturn(true);
        when(jwtService.extractClaim(eq("reset-token"), any())).thenReturn("password_reset");
        when(jwtService.extractUsername("reset-token")).thenReturn(user.getEmail());


        assertThatThrownBy(() -> passwordManagementService.resetPassword("reset-token", dto))
                .isInstanceOf(UserResetPasswordException.class);

        verify(userRepository, never()).save(user);
        verify(refreshTokenService, never()).createRefreshToken(user);
        verify(jwtService, never()).generateToken(any());
        verify(authEmailService, never()).sendPasswordChangedNotification(user.getEmail());
    }

    @Test
    void shouldUpdatePasswordAndSendNotification() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        UpdatePasswordRequestDto dto = new UpdatePasswordRequestDto("OldStrongPass!1", "NewStrongPass!1", "NewStrongPass!1");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(dto.currentPassword(), user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode(dto.newPassword())).thenReturn("encoded-password");

        passwordManagementService.updatePassword(dto, user.getEmail());

        assertThat(user.getPassword()).isEqualTo("encoded-password");
        verify(userRepository).save(user);
        verify(authEmailService).sendPasswordChangedNotification(user.getEmail());
    }

    @Test
    void shouldRejectUpdatePasswordWhenCurrentPasswordIsInvalid() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        UpdatePasswordRequestDto dto = new UpdatePasswordRequestDto("wrong", "NewStrongPass!1", "NewStrongPass!1");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(dto.currentPassword(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> passwordManagementService.updatePassword(dto, user.getEmail()))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void shouldRejectUpdatePasswordWhenConfirmationDoesNotMatch() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        UpdatePasswordRequestDto dto = new UpdatePasswordRequestDto("OldStrongPass!1", "NewStrongPass!1", "OtherStrongPass!1");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(dto.currentPassword(), user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> passwordManagementService.updatePassword(dto, user.getEmail()))
                .isInstanceOf(InvalidPasswordException.class);
    }
}
