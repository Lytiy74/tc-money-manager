package org.tc.mtracker.auth.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tc.mtracker.auth.dto.ResetPasswordRequestDto;
import org.tc.mtracker.auth.dto.UpdatePasswordRequestDto;
import org.tc.mtracker.auth.mail.AuthEmailService;
import org.tc.mtracker.auth.model.RefreshToken;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;
import org.tc.mtracker.utils.exceptions.InvalidPasswordException;
import org.tc.mtracker.utils.exceptions.UserNotFoundException;
import org.tc.mtracker.utils.exceptions.UserResetPasswordException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordManagementService {
    private static final String PASSWORD_RESET_PURPOSE = "password_reset";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String BAD_CREDENTIALS_MESSAGE = "Invalid email or password.";
    private static final String PASSWORDS_DO_NOT_MATCH_MESSAGE = "Passwords do not match.";
    private static final String CURRENT_PASSWORD_INCORRECT_MESSAGE = "Current password is incorrect.";
    private static final String SAME_PASSWORD_RESET_MESSAGE = "New password cannot be the same as the current one.";

    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthEmailService authEmailService;

    public void sendTokenToResetPassword(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> {
                    log.warn("Password reset request rejected: user not found for email={}", email);
                    return new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
                }
        );

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String resetToken = jwtService.generateToken(Map.of(PURPOSE_CLAIM, PASSWORD_RESET_PURPOSE), userDetails);

        authEmailService.sendResetPassword(user.getEmail(), resetToken);
        log.info("Reset password token sent to user's email with id: {}", user.getId());
    }

    @Transactional
    public JwtResponseDTO resetPassword(String token, ResetPasswordRequestDto dto) {
        validateResetPasswordConfirmation(dto.password(), dto.confirmPassword());

        String purpose = jwtService.extractClaim(token, claims -> claims.get(PURPOSE_CLAIM, String.class));
        if (!PASSWORD_RESET_PURPOSE.equals(purpose)) {
            log.warn("Password reset rejected: invalid token purpose={}", purpose);
            throw new JwtException("Invalid token purpose");
        }

        String email = jwtService.extractUsername(token);
        User user = findUserByEmail(email);

        updateEncodedPassword(user, dto.password());

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("Password successfully changed for user with id: {}", user.getId());
        return new JwtResponseDTO(accessToken, refreshToken.getToken());
    }

    public void updatePassword(UpdatePasswordRequestDto dto, String currentUserEmail) {
        User user = findUserByEmail(currentUserEmail);

        validateCurrentPassword(user, dto.currentPassword());
        validateUpdatedPasswordConfirmation(
                dto.newPassword(),
                dto.confirmNewPassword(),
                user.getId()
        );
        ensureUpdatedPasswordDiffersFromCurrent(user, dto.newPassword(), user.getId());

        updateEncodedPassword(user, dto.newPassword());
        authEmailService.sendPasswordChangedNotification(user.getEmail());
        log.info("Password for user with id {} is updated successfully!", user.getId());
    }

    private void validateCurrentPassword(User user, String rawCurrentPassword) {
        if (!passwordEncoder.matches(rawCurrentPassword, user.getPassword())) {
            log.warn("Password update rejected: current password mismatch for userId={}", user.getId());
            throw new InvalidPasswordException(CURRENT_PASSWORD_INCORRECT_MESSAGE);
        }
    }

    private void validateResetPasswordConfirmation(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            log.warn("Password reset rejected: confirmation mismatch");
            throw new UserResetPasswordException(PASSWORDS_DO_NOT_MATCH_MESSAGE);
        }
    }

    private void validateUpdatedPasswordConfirmation(String password, String confirmation, Long userId) {
        if (!password.equals(confirmation)) {
            log.warn("Password update rejected: confirmation mismatch for userId={}", userId);
            throw new InvalidPasswordException(PASSWORDS_DO_NOT_MATCH_MESSAGE);
        }
    }

    private void ensureUpdatedPasswordDiffersFromCurrent(User user, String rawPassword, Long userId) {
        if (passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("Password update rejected: new password is the same as the current one for userId={}", userId);
            throw new InvalidPasswordException(SAME_PASSWORD_RESET_MESSAGE);
        }
    }

    private void updateEncodedPassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(
                () -> {
                    log.warn("Password management flow failed: user not found for email={}", email);
                    return new UserNotFoundException("User not found.");
                }
        );
    }
}
