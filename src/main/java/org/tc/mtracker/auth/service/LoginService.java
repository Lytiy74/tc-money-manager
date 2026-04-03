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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    public JwtResponseDTO login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.email()).orElseThrow(
                () -> new BadCredentialsException("User with email " + dto.email() + " does not exist.")
        );

        if (!user.isActivated()) {
            throw new UserNotActivatedException("User with id " + user.getId() + " is not activated yet.");
        }

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials. Password does not match!");
        }

        String accessToken = jwtService.generateToken(new CustomUserDetails(user));
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("User with id {} is authenticated successfully.", user.getId());
        return new JwtResponseDTO(accessToken, refreshToken.getToken());
    }
}
