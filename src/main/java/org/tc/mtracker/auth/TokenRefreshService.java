package org.tc.mtracker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tc.mtracker.auth.dto.RefreshTokenRequest;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.security.JwtService;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRefreshService {
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public JwtResponseDTO refreshToken(RefreshTokenRequest request) {
        return refreshTokenService.findByToken(request.refreshToken())
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtService.generateToken(new CustomUserDetails(user));
                    return new JwtResponseDTO(accessToken, request.refreshToken());
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database"));
    }
}
