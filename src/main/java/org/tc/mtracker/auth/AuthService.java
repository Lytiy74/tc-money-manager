package org.tc.mtracker.auth;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.account.Account;
import org.tc.mtracker.account.AccountRepository;
import org.tc.mtracker.auth.dto.*;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtResponseDTO;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.exceptions.UserAlreadyActivatedException;
import org.tc.mtracker.utils.exceptions.UserAlreadyExistsException;
import org.tc.mtracker.utils.exceptions.UserNotActivatedException;
import org.tc.mtracker.utils.exceptions.UserResetPasswordException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String EMAIL_VERIFICATION_PURPOSE = "email_verification";

    private final UserRepository userRepository;
    private final UserService userService;
    private final AccountRepository accountRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final S3Service imageService;
    private final RefreshTokenService refreshTokenService;

    private final AuthMapper authMapper;

    @Transactional
    public AuthResponseDTO signUp(AuthRequestDTO dto, MultipartFile avatar) {
        if (userService.isExistsByEmail(dto.email())) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }

        String imageKey = (avatar == null || avatar.isEmpty()) ? null : UUID.randomUUID().toString();
        String avatarUrl = uploadAvatar(imageKey, avatar);

        User user = User.builder()
                .email(dto.email())
                .fullName(dto.fullName())
                .password(passwordEncoder.encode(dto.password()))
                .currencyCode(dto.currencyCode())
                .avatarId(imageKey)
                .activated(false)
                .build();
        User savedUser = userService.save(user);

        Account defaultAccount = Account.builder()
                .user(savedUser)
                .balance(BigDecimal.ZERO)
                .build();
        Account savedDefaultAccount = accountRepository.save(defaultAccount);

        savedUser.addAccount(savedDefaultAccount);
        savedUser.setDefaultAccount(savedDefaultAccount);
        savedUser = userService.save(savedUser);

        emailService.sendVerificationEmail(savedUser);
        log.info("User with id {} is registered successfully.", savedUser.getId());
        return authMapper.toAuthResponseDTO(savedUser, avatarUrl);
    }

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
    /**
     * Searches user by requested email and sends a link if it exists.
     * @param email requested email
     */
    public void sendTokenToResetPassword(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new BadCredentialsException("User with email " + email + " does not exist.")
        );

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String resetToken = jwtService.generateToken(Map.of("purpose", "password_reset"), userDetails);

        emailService.sendResetPassword(user, resetToken);
        log.info("Reset password token sent to user's email with id: {}", user.getId());
    }
    /**
     * Method to reset user's password
     * @param token token from email link
     * @param dto new password and password confirm
     * @return access token in good case
     */
    @Transactional
    public JwtResponseDTO resetPassword(String token, ResetPasswordDTO dto) {
        if (!dto.password().equals(dto.confirmPassword())) {
            throw new UserResetPasswordException("Passwords do not match!");
        }

        String purpose = jwtService.extractClaim(token, claims -> claims.get("purpose", String.class));
        if (!"password_reset".equals(purpose)) {
            throw new JwtException("Invalid token purpose");
        }

        String email = jwtService.extractUsername(token);
        User user = userService.findByEmail(email);

        user.setPassword(passwordEncoder.encode(dto.password()));
        userService.save(user);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("Password successfully changed for user with id: {}", user.getId());
        return new JwtResponseDTO(accessToken, refreshToken.getToken());
    }

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


    private @Nullable String uploadAvatar(String imageKey, MultipartFile avatar) {
        if (imageKey == null || avatar == null || avatar.isEmpty()) {
            return null;
        }

        imageService.saveFile(imageKey, avatar);
        return imageService.generatePresignedUrl(imageKey);
    }
}

