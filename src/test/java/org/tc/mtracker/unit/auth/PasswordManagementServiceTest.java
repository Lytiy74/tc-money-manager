package org.tc.mtracker.unit.auth;

import io.jsonwebtoken.JwtException;
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

    private static final String EMAIL = "user@example.com";
    private static final String RESET_TOKEN = "reset-token";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String CURRENT_PASSWORD = "OldStrongPass!1";
    private static final String NEW_PASSWORD = "NewStrongPass!1";
    private static final String ENCODED_PASSWORD = "encoded-password";

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
        User user = user();
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(NEW_PASSWORD, NEW_PASSWORD);
        RefreshToken refreshToken = refreshToken(user);

        mockResetToken(RESET_TOKEN, user);
        when(passwordEncoder.encode(dto.password())).thenReturn(ENCODED_PASSWORD);
        when(jwtService.generateToken(any())).thenReturn(ACCESS_TOKEN);
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

        JwtResponseDTO result = passwordManagementService.resetPassword(RESET_TOKEN, dto);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(user.getPassword()).isEqualTo(ENCODED_PASSWORD);
        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectResetPasswordWhenConfirmationDoesNotMatch() {
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(NEW_PASSWORD, "Mismatch!1");

        assertThatThrownBy(() -> passwordManagementService.resetPassword(RESET_TOKEN, dto))
                .isInstanceOf(UserResetPasswordException.class)
                .hasMessage("Passwords do not match.");

        verifyNoInteractions(jwtService, userRepository, passwordEncoder, refreshTokenService);
    }

    @Test
    void shouldRejectResetPasswordWhenTokenPurposeIsInvalid() {
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto(NEW_PASSWORD, NEW_PASSWORD);
        when(jwtService.extractClaim(eq(RESET_TOKEN), any())).thenReturn("email_verification");

        assertThatThrownBy(() -> passwordManagementService.resetPassword(RESET_TOKEN, dto))
                .isInstanceOf(JwtException.class)
                .hasMessage("Invalid token purpose");

        verify(userRepository, never()).findByEmail(any());
        verifyNoInteractions(passwordEncoder, refreshTokenService, authEmailService);
    }

    @Test
    void shouldUpdatePasswordAndSendNotification() {
        User user = user();
        UpdatePasswordRequestDto dto = new UpdatePasswordRequestDto(CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        mockExistingUser(user);
        mockCurrentAndNewPasswordValidation(user, dto, false);
        when(passwordEncoder.encode(dto.newPassword())).thenReturn(ENCODED_PASSWORD);

        passwordManagementService.updatePassword(dto, user.getEmail());

        assertThat(user.getPassword()).isEqualTo(ENCODED_PASSWORD);
        verify(userRepository).save(user);
        verify(authEmailService).sendPasswordChangedNotification(user.getEmail());
    }

    @Test
    void shouldRejectUpdatePasswordWhenCurrentPasswordIsInvalid() {
        User user = user();
        UpdatePasswordRequestDto dto = new UpdatePasswordRequestDto("wrong", NEW_PASSWORD, NEW_PASSWORD);

        mockExistingUser(user);
        when(passwordEncoder.matches(dto.currentPassword(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> passwordManagementService.updatePassword(dto, user.getEmail()))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Current password is incorrect.");

        verify(userRepository, never()).save(any());
        verify(authEmailService, never()).sendPasswordChangedNotification(any());
    }

    @Test
    void shouldRejectUpdatePasswordWhenConfirmationDoesNotMatch() {
        User user = user();
        UpdatePasswordRequestDto dto = new UpdatePasswordRequestDto(CURRENT_PASSWORD, NEW_PASSWORD, "OtherStrongPass!1");

        mockExistingUser(user);
        when(passwordEncoder.matches(dto.currentPassword(), user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> passwordManagementService.updatePassword(dto, user.getEmail()))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Passwords do not match.");

        verify(userRepository, never()).save(any());
        verify(authEmailService, never()).sendPasswordChangedNotification(any());
    }

    @Test
    void shouldRejectUpdatePasswordWhenNewPasswordMatchesCurrentPassword() {
        User user = user();
        UpdatePasswordRequestDto dto = new UpdatePasswordRequestDto(CURRENT_PASSWORD, CURRENT_PASSWORD, CURRENT_PASSWORD);

        mockExistingUser(user);
        mockCurrentAndNewPasswordValidation(user, dto, true);

        assertThatThrownBy(() -> passwordManagementService.updatePassword(dto, user.getEmail()))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("New password cannot be the same as the current one.");

        verify(userRepository, never()).save(any());
        verify(authEmailService, never()).sendPasswordChangedNotification(any());
    }

    private void mockResetToken(String token, User user) {
        when(jwtService.extractClaim(eq(token), any())).thenReturn("password_reset");
        when(jwtService.extractUsername(token)).thenReturn(user.getEmail());
        mockExistingUser(user);
    }

    private void mockExistingUser(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private void mockCurrentAndNewPasswordValidation(User user, UpdatePasswordRequestDto dto, boolean sameAsCurrent) {
        when(passwordEncoder.matches(dto.currentPassword(), user.getPassword())).thenReturn(true);
        when(passwordEncoder.matches(dto.newPassword(), user.getPassword())).thenReturn(sameAsCurrent);
    }

    private User user() {
        return EntityTestFactory.user(1L, EMAIL, true);
    }

    private RefreshToken refreshToken(User user) {
        return EntityTestFactory.refreshToken(REFRESH_TOKEN, user, LocalDateTime.now().plusDays(1));
    }
}
