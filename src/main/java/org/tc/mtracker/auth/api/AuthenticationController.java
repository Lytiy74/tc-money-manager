package org.tc.mtracker.auth.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.auth.dto.*;
import org.tc.mtracker.auth.service.*;
import org.tc.mtracker.security.JwtResponseDTO;

@RestController
@RequestMapping(value = "/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and email verification endpoints")
@Validated
public class AuthenticationController implements AuthenticationApi {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final PasswordManagementService passwordManagementService;
    private final EmailVerificationService emailVerificationService;
    private final TokenRefreshService tokenRefreshService;

    @Override
    public ResponseEntity<RegistrationResponseDto> signUp(RegistrationRequestDto registrationRequestDto, MultipartFile avatar) {
        RegistrationResponseDto registrationResponseDto = registrationService.signUp(registrationRequestDto, avatar);
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationResponseDto);
    }

    @Override
    public ResponseEntity<JwtResponseDTO> login(LoginRequestDto loginRequestDto) {
        JwtResponseDTO jwt = loginService.login(loginRequestDto);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(jwt);
    }

    @Override
    public ResponseEntity<String> sendResetPasswordToken(String email) {
        passwordManagementService.sendTokenToResetPassword(email);
        return ResponseEntity.ok("Your link to reset password was sent!");
    }

    @Override
    public ResponseEntity<JwtResponseDTO> resetPassword(String token, ResetPasswordRequestDto resetPasswordRequestDto) {
        JwtResponseDTO response = passwordManagementService.resetPassword(token, resetPasswordRequestDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<JwtResponseDTO> verifyToken(String token) {
        JwtResponseDTO jwt = emailVerificationService.verifyToken(token);
        return ResponseEntity.ok().body(jwt);
    }

    @Override
    public ResponseEntity<JwtResponseDTO> refreshToken(RefreshTokenRequestDto request) {
        JwtResponseDTO jwt = tokenRefreshService.refreshToken(request);
        return ResponseEntity.ok(jwt);
    }
}
