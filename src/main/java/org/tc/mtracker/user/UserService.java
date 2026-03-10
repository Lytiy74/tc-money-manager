package org.tc.mtracker.user;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.auth.EmailService;
import org.tc.mtracker.security.CustomUserDetails;
import org.tc.mtracker.security.JwtService;
import org.tc.mtracker.user.dto.*;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.exceptions.EmailVerificationException;
import org.tc.mtracker.utils.exceptions.InvalidPasswordException;
import org.tc.mtracker.utils.exceptions.UserAlreadyExistsException;
import org.tc.mtracker.utils.exceptions.UserNotFoundException;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public User save(User user) {
        return userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    public boolean isExistsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public ResponseUserDTO getUser(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new UserNotFoundException("User with username '" + auth.getName() + "' not found")
        );

        String avatarUrl = generateAvatarUrl(user);
        return userMapper.toDto(user, avatarUrl);
    }

    public User getCurrentAuthenticatedUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new UserNotFoundException("User with username '" + auth.getName() + "' not found")
        );
    }

    @Transactional
    public ResponseUserDTO updateProfile(RequestUpdateUserProfileDTO dto, MultipartFile avatar, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new UserNotFoundException("User was not found!")
        );

        if (avatar != null) {
            uploadAvatar(avatar, user);
        }

        userMapper.updateEntityFromDto(dto, user);

        userRepository.save(user);

        String avatarUrl = generateAvatarUrl(user);
        log.info("User with id {} is updated successfully!", user.getId());

        return userMapper.toDto(user, avatarUrl);
    }

    @Transactional
    public void updateEmail(RequestUpdateUserEmailDTO dto, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new UserNotFoundException("User was not found!")
        );

        if (userRepository.findByEmail(dto.email()).isPresent()) {
            throw new UserAlreadyExistsException("Email already used");
        }

        user.setPendingEmail(dto.email());
        String generatedToken = jwtService.generateToken(
                Map.of("purpose", "email_update_verification"),
                new CustomUserDetails(user)
        );

        user.setVerificationToken(generatedToken);

        emailService.sendVerificationEmail(dto.email(), generatedToken);

        userRepository.save(user);
    }

    @Transactional
    public void verifyEmailUpdate(String token) {
        String purpose = jwtService.extractClaim(token, claims -> claims.get("purpose", String.class));
        if (!"email_update_verification".equals(purpose)) {
            throw new JwtException("Invalid token type for verification");
        }

        String email = jwtService.extractUsername(token);
        User user = userRepository.findByEmail(email).orElseThrow();

        if (user.getVerificationToken() == null || !token.equals(user.getVerificationToken())) {
            throw new EmailVerificationException("Invalid token for verification");
        }

        user.setEmail(user.getPendingEmail());
        user.setPendingEmail(null);
        user.setVerificationToken(null);
        userRepository.save(user);
    }

    public void updatePassword(RequestUpdateUserPasswordDTO dto, Authentication auth) {
        User user = findByEmail(auth.getName());

        verifyCurrentPasswordWithUserInput(dto.currentPassword(), user.getPassword());
        verifyPasswordConfirmation(dto.newPassword(), dto.confirmNewPassword());
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
        sendPasswordChangeNotification(user.getEmail());
        log.info("Password for user with id {} is updated successfully!", user.getId());
    }

    private String generateAvatarUrl(User user) {
        return user.getAvatarId() != null ? s3Service.generatePresignedUrl(user.getAvatarId()) : null;
    }

    private void uploadAvatar(MultipartFile avatar, User user) {
        String avatarId = user.getAvatarId();
        if (avatarId == null) {
            avatarId = UUID.randomUUID().toString();
            user.setAvatarId(avatarId);
        }
        s3Service.saveFile(avatarId, avatar);
        log.info("Avatar with id {} is uploaded successfully!", avatarId);
    }

    private void sendPasswordChangeNotification(String email) {
        emailService.sendPlainTextEmail(email, "Password changed", "Your password has been changed successfully.");
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
