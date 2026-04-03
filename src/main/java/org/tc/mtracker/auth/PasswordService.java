package org.tc.mtracker.auth;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tc.mtracker.auth.dto.ResetPasswordDTO;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;
import org.tc.mtracker.user.dto.RequestUpdateUserPasswordDTO;
import org.tc.mtracker.utils.exceptions.InvalidPasswordException;
import org.tc.mtracker.utils.exceptions.UserNotFoundException;
import org.tc.mtracker.utils.exceptions.UserResetPasswordException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {
    private static final String PASSWORD_RESET_PURPOSE = "password_reset";

    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final EmailService emailService;

    public void sendTokenToResetPassword(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new BadCredentialsException("User with email " + email + " does not exist.")
        );

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String resetToken = jwtService.generateToken(Map.of("purpose", PASSWORD_RESET_PURPOSE), userDetails);

        emailService.sendResetPassword(user.getEmail(), resetToken);
        log.info("Reset password token sent to user's email with id: {}", user.getId());
    }

    @Transactional
    public JwtResponseDTO resetPassword(String token, ResetPasswordDTO dto) {
        if (!dto.password().equals(dto.confirmPassword())) {
            throw new UserResetPasswordException("Passwords do not match!");
        }

        String purpose = jwtService.extractClaim(token, claims -> claims.get("purpose", String.class));
        if (!PASSWORD_RESET_PURPOSE.equals(purpose)) {
            throw new JwtException("Invalid token purpose");
        }

        String email = jwtService.extractUsername(token);
        User user = findUserByEmail(email);

        user.setPassword(passwordEncoder.encode(dto.password()));
        userRepository.save(user);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("Password successfully changed for user with id: {}", user.getId());
        return new JwtResponseDTO(accessToken, refreshToken.getToken());
    }

    public void updatePassword(RequestUpdateUserPasswordDTO dto, String currentUserEmail) {
        User user = findUserByEmail(currentUserEmail);

        verifyCurrentPasswordWithUserInput(dto.currentPassword(), user.getPassword());
        verifyPasswordConfirmation(dto.newPassword(), dto.confirmNewPassword());
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
        emailService.sendPasswordChangedNotification(user.getEmail());
        log.info("Password for user with id {} is updated successfully!", user.getId());
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(
                () -> new UserNotFoundException("User with email '%s' not found".formatted(email))
        );
    }

    private void verifyPasswordConfirmation(String firstPassword, String secondPassword) {
        if (!firstPassword.equals(secondPassword)) {
            throw new InvalidPasswordException("Password mismatch");
        }
    }

    private void verifyCurrentPasswordWithUserInput(String passwordToCheck, String currentUserPassword) {
        if (!passwordEncoder.matches(passwordToCheck, currentUserPassword)) {
            throw new InvalidPasswordException("Password mismatch");
        }
    }
}
