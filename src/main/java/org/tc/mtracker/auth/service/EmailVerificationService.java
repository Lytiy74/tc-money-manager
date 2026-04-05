package org.tc.mtracker.auth.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tc.mtracker.auth.dto.UpdateEmailRequestDto;
import org.tc.mtracker.auth.mail.AuthEmailService;
import org.tc.mtracker.auth.model.RefreshToken;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;
import org.tc.mtracker.utils.exceptions.EmailVerificationException;
import org.tc.mtracker.utils.exceptions.UserAlreadyActivatedException;
import org.tc.mtracker.utils.exceptions.UserAlreadyExistsException;
import org.tc.mtracker.utils.exceptions.UserNotFoundException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {
    private static final String EMAIL_VERIFICATION_PURPOSE = "email_verification";
    private static final String EMAIL_UPDATE_VERIFICATION_PURPOSE = "email_update_verification";

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final AuthEmailService authEmailService;

    public JwtResponseDTO verifyToken(String token) {
        String purpose = jwtService.extractClaim(token, claims -> claims.get("purpose", String.class));
        if (!EMAIL_VERIFICATION_PURPOSE.equals(purpose)) {
            log.warn("Email verification rejected: invalid token purpose={}", purpose);
            throw new JwtException("Invalid token purpose");
        }

        String email = jwtService.extractUsername(token);
        User user = findUserByEmail(email);

        if (user.isActivated()) {
            log.warn("Email verification skipped: user already activated email={}", email);
            throw new UserAlreadyActivatedException("Account is already activated.");
        }

        user.setActivated(true);
        userRepository.save(user);

        String accessToken = jwtService.generateToken(new CustomUserDetails(user));
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("User with id {} is verified successfully.", user.getId());
        return new JwtResponseDTO(accessToken, refreshToken.getToken());
    }

    @Transactional
    public void updateEmail(UpdateEmailRequestDto dto, String currentUserEmail) {
        User user = findUserByEmail(currentUserEmail);

        if (userRepository.existsByEmail(dto.email())) {
            log.warn("Email update rejected: target email already exists currentEmail={} targetEmail={}", currentUserEmail, dto.email());
            throw new UserAlreadyExistsException("User with this email already exists.");
        }

        user.setPendingEmail(dto.email());
        String generatedToken = jwtService.generateToken(
                Map.of("purpose", EMAIL_UPDATE_VERIFICATION_PURPOSE),
                new CustomUserDetails(user)
        );

        user.setVerificationToken(generatedToken);

        authEmailService.sendVerificationEmail(dto.email(), generatedToken);

        userRepository.save(user);
        log.info("Email update verification initiated for userId={} targetEmail={}", user.getId(), dto.email());
    }

    @Transactional
    public void verifyEmailUpdate(String token) {
        String purpose = jwtService.extractClaim(token, claims -> claims.get("purpose", String.class));
        if (!EMAIL_UPDATE_VERIFICATION_PURPOSE.equals(purpose)) {
            log.warn("Email update verification rejected: invalid token purpose={}", purpose);
            throw new JwtException("Invalid token purpose");
        }

        String email = jwtService.extractUsername(token);
        User user = findUserByEmail(email);

        if (user.getVerificationToken() == null || !token.equals(user.getVerificationToken())) {
            log.warn("Email update verification rejected: token mismatch for userId={}", user.getId());
            throw new EmailVerificationException("Invalid verification token.");
        }

        user.setEmail(user.getPendingEmail());
        user.setPendingEmail(null);
        user.setVerificationToken(null);
        userRepository.save(user);
        log.info("Email updated successfully for userId={}", user.getId());
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(
                () -> {
                    log.warn("Email verification flow failed: user not found for email={}", email);
                    return new UserNotFoundException("User not found.");
                }
        );
    }
}
