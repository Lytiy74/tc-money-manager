package org.tc.mtracker.auth;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.exceptions.UserAlreadyActivatedException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {
    private static final String EMAIL_VERIFICATION_PURPOSE = "email_verification";
    private final JwtService jwtService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;


    public JwtResponseDTO verifyToken(String token) {
        String purpose = jwtService.extractClaim(token, claims -> claims.get("purpose", String.class));
        if (!EMAIL_VERIFICATION_PURPOSE.equals(purpose)) {
            throw new JwtException("Invalid token type for verification");
        }

        String email = jwtService.extractUsername(token);
        User user = userService.findByEmail(email);

        if (user.isActivated()) {
            throw new UserAlreadyActivatedException("User with email " + email + " is already activated");
        }

        user.setActivated(true);
        userService.save(user);

        String accessToken = jwtService.generateToken(new CustomUserDetails(user));
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("User with id {} is verified successfully.", user.getId());
        return new JwtResponseDTO(accessToken, refreshToken.getToken());
    }
}
