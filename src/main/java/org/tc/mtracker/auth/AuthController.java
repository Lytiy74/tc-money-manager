package org.tc.mtracker.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.auth.dto.*;
import org.tc.mtracker.common.image.ValidImage;
import org.tc.mtracker.security.JwtResponseDTO;

@RestController
@RequestMapping(value = "/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and email verification endpoints")
@Validated
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;

    @Operation(summary = "Sign up a new user",
            description = "Creates a new user and sends email verification link. Account not activated until verified")
    @ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = AuthResponseDTO.class))
            }
    )
    @ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProblemDetail.class))
            }
    )
    @ApiResponse(
            responseCode = "409",
            description = "User with this email already exists",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProblemDetail.class))
            }
    )
    @PostMapping(value = "/sign-up", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponseDTO> signUp(
            @Parameter(
                    name = "User dto",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthRequestDTO.class))
            )
            @Valid
            @RequestPart(name = "dto") AuthRequestDTO authRequestDTO,

            @Parameter(
                    name = "Avatar",
                    required = false,
                    content = {
                            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))
                    }
            )
            @ValidImage
            @RequestPart(name = "avatar", required = false) MultipartFile avatar

    ) {
        AuthResponseDTO authResponseDTO = registrationService.signUp(authRequestDTO, avatar);
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponseDTO);
    }

    @Operation(
            summary = "Login user",
            description = "Logins user and returns an access token."
    )
    @ApiResponse(
            responseCode = "200",
            description = "User logged successfully",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = JwtResponseDTO.class))
            }
    )
    @ApiResponse(
            responseCode = "401",
            description = "Authentication failed",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProblemDetail.class))
            }
    )
    @ApiResponse(
            responseCode = "400",
            description = "Fields were filled incorrectly",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProblemDetail.class))
            }
    )
    @ApiResponse(
            responseCode = "403",
            description = "Access Denied",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProblemDetail.class))
            }
    )
    @ApiResponse(
            responseCode = "500",
            description = "Internal Server Error",
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProblemDetail.class))
            }
    )
    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User login details",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequestDto.class))
            )
            @Valid @RequestBody LoginRequestDto loginRequestDto
    ) {
        JwtResponseDTO jwt = authService.login(loginRequestDto);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(jwt);
    }

    /**
     * Sends to user's email a link with token to be able to reset user's password
     *
     * @param email requested email for resetting password
     * @return Http status code and message
     */
    @Operation(
            summary = "Send reset password email",
            description = "Generates a reset link and sends it to the user's email."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Reset link sent successfully",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(type = "string", example = "Your link to reset password was sent!")
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "User with requested email does not exist",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @PostMapping("/getTokenToResetPassword")
    public ResponseEntity<String> sendResetPasswordToken(
            @RequestParam("email") @Email String email
    ) {
        authService.sendTokenToResetPassword(email);
        return ResponseEntity.ok("Your link to reset password was sent!");
    }

    /**
     * Resets user's password
     *
     * @param token            token from email link
     * @param resetPasswordDTO new user's password and confirm password
     * @return access token to login in good case
     */
    @Operation(
            summary = "Reset password using token",
            description = "Updates the user password if the token is valid."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Password updated successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = JwtResponseDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request (Passwords do not match or validation failed)",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "Token expired or invalid purpose",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @PostMapping("/reset-password/confirm")
    public ResponseEntity<JwtResponseDTO> resetPassword(
            @RequestParam("token") String token,
            @Valid @RequestBody ResetPasswordDTO resetPasswordDTO
    ) {
        JwtResponseDTO response = authService.resetPassword(token, resetPasswordDTO);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Verify email by verification token",
            description = "Activates user account using email verification token and returns access JWT."
    )
    @ApiResponse(
            responseCode = "200",
            description = "User activated, access token issued",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = JwtResponseDTO.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid token (wrong purpose/format)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "User already activated",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "JWT parsing/validation error",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class))
    )
    @GetMapping("/verify")
    public ResponseEntity<JwtResponseDTO> verifyToken(
            @Parameter(
                    name = "token",
                    in = ParameterIn.QUERY,
                    description = "Email verification token JWT with purpose=email_verification",
                    required = true,
                    schema = @Schema(type = "string")
            )
            @RequestParam String token) {
        JwtResponseDTO jwt = authService.verifyToken(token);
        return ResponseEntity.ok().body(jwt);
    }

    /**
     * Refresh authentication token using refresh token
     */
    @Operation(
            summary = "Refresh token",
            description = "Generates a new access token using a valid refresh token"
    )
    @ApiResponse(responseCode = "200", description = "Token successfully refreshed",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "Token refreshed",
                            value = "{ \"accessToken\": \"new-jwt-access-token\", \"refreshToken\": \"new-jwt-refresh-token\" }"
                    )))
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponseDTO> refreshToken(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refresh token payload",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = RefreshTokenRequest.class),
                            examples = @ExampleObject(
                                    name = "Sample refresh request",
                                    value = "{ \"refreshToken\": \"jwt-refresh-token\" }"
                            )
                    )
            )
            @Valid @RequestBody RefreshTokenRequest request) {
        JwtResponseDTO jwt = authService.refreshToken(request);
        return ResponseEntity.ok(jwt);
    }
}
