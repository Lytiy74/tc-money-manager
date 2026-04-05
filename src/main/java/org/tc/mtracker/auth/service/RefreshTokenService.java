package org.tc.mtracker.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tc.mtracker.auth.model.RefreshToken;
import org.tc.mtracker.auth.repository.RefreshTokenRepository;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;
import org.tc.mtracker.utils.exceptions.InvalidRefreshTokenException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public RefreshToken createRefreshToken(User user) {
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpiration()))
                .build();

        RefreshToken savedToken = refreshTokenRepository.save(refreshToken);
        log.debug("Refresh token created for userId={}", user.getId());
        return savedToken;
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            log.warn("Rejected expired refresh token for userId={}", token.getUser().getId());
            throw new InvalidRefreshTokenException("Refresh token expired. Please sign in again.");
        }
        return token;
    }

    @Scheduled(fixedRate = 86400000)
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
    }
}
