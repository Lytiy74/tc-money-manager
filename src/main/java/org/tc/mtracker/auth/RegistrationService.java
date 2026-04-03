package org.tc.mtracker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.account.Account;
import org.tc.mtracker.account.AccountRepository;
import org.tc.mtracker.auth.dto.AuthMapper;
import org.tc.mtracker.auth.dto.AuthRequestDTO;
import org.tc.mtracker.auth.dto.AuthResponseDTO;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.exceptions.UserAlreadyExistsException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final S3Service imageService;


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

    private @Nullable String uploadAvatar(String imageKey, MultipartFile avatar) {
        if (imageKey == null || avatar == null || avatar.isEmpty()) {
            return null;
        }

        imageService.saveFile(imageKey, avatar);
        return imageService.generatePresignedUrl(imageKey);
    }
}
