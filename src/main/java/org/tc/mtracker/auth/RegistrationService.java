package org.tc.mtracker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.account.DefaultAccountProvisioningService;
import org.tc.mtracker.auth.dto.AuthMapper;
import org.tc.mtracker.auth.dto.AuthRequestDTO;
import org.tc.mtracker.auth.dto.AuthResponseDTO;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.exceptions.UserAlreadyExistsException;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {
    private static final String EMAIL_VERIFICATION_PURPOSE = "email_verification";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final DefaultAccountProvisioningService defaultAccountProvisioningService;
    private final EmailService emailService;
    private final S3Service imageService;
    private final JwtService jwtService;


    @Transactional
    public AuthResponseDTO signUp(AuthRequestDTO dto, MultipartFile avatar) {
        if (userRepository.existsByEmail(dto.email())) {
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
        User savedUser = userRepository.save(user);
        savedUser = defaultAccountProvisioningService.provisionDefaultAccount(savedUser);

        String verificationToken = jwtService.generateToken(
                Map.of("purpose", EMAIL_VERIFICATION_PURPOSE),
                new CustomUserDetails(savedUser)
        );

        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);
        log.info("User with id {} is registered successfully.", savedUser.getId());
        return authMapper.toAuthResponseDTO(savedUser, avatarUrl);
    }

    private @Nullable String uploadAvatar(String imageKey, MultipartFile avatar) {
        if (imageKey == null || avatar == null || avatar.isEmpty()) {
            return null;
        }

        imageService.saveFile(imageKey, avatar);
        return imageService.generatePresignedUrl(imageKey);
    }
}
