package org.tc.mtracker.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.user.dto.RequestUpdateUserProfileDTO;
import org.tc.mtracker.user.dto.ResponseUserDTO;
import org.tc.mtracker.user.dto.UserMapper;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.exceptions.UserNotFoundException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    public ResponseUserDTO getUser(Authentication auth) {
        return getUser(auth.getName());
    }

    public User getCurrentAuthenticatedUser(Authentication auth) {
        return getCurrentAuthenticatedUser(auth.getName());
    }

    public ResponseUserDTO getUser(String currentUserEmail) {
        User user = getCurrentAuthenticatedUser(currentUserEmail);
        return userMapper.toDto(user, generateAvatarUrl(user));
    }

    public User getCurrentAuthenticatedUser(String currentUserEmail) {
        return userRepository.findByEmail(currentUserEmail).orElseThrow(
                () -> new UserNotFoundException("User with email '%s' not found".formatted(currentUserEmail))
        );
    }

    @Transactional
    public ResponseUserDTO updateProfile(RequestUpdateUserProfileDTO dto, MultipartFile avatar, Authentication auth) {
        return updateProfile(dto, avatar, auth.getName());
    }

    @Transactional
    public ResponseUserDTO updateProfile(RequestUpdateUserProfileDTO dto, MultipartFile avatar, String currentUserEmail) {
        User user = getCurrentAuthenticatedUser(currentUserEmail);

        if (avatar != null) {
            uploadAvatar(avatar, user);
        }

        userMapper.updateEntityFromDto(dto, user);

        userRepository.save(user);

        String avatarUrl = generateAvatarUrl(user);
        log.info("User with id {} is updated successfully!", user.getId());

        return userMapper.toDto(user, avatarUrl);
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


}
