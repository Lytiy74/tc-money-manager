package org.tc.mtracker.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.tc.mtracker.auth.dto.LoginRequestDto;
import org.tc.mtracker.auth.model.RefreshToken;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;
import org.tc.mtracker.utils.exceptions.UserNotActivatedException;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginService {
    private static final String BAD_CREDENTIALS_MESSAGE = "Invalid email or password.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    public JwtResponseDTO login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.email()).orElseThrow(
                () -> {
                    log.warn("Login failed: user not found for email={}", dto.email());
                    return new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
                }
        );

        if (!user.isActivated()) {
            log.warn("Login failed: account is not activated for userId={} email={}", user.getId(), user.getEmail());
            throw new UserNotActivatedException("Account is not activated yet.");
        }

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            log.warn("Login failed: invalid password for email={}", dto.email());
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }

        String accessToken = jwtService.generateToken(new CustomUserDetails(user));
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("User with id {} is authenticated successfully.", user.getId());
        return new JwtResponseDTO(accessToken, refreshToken.getToken());
    }
}
