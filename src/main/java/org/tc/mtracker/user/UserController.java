package org.tc.mtracker.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.auth.EmailVerificationService;
import org.tc.mtracker.auth.PasswordService;
import org.tc.mtracker.common.image.ValidImage;
import org.tc.mtracker.user.dto.RequestUpdateUserEmailDTO;
import org.tc.mtracker.user.dto.RequestUpdateUserPasswordDTO;
import org.tc.mtracker.user.dto.RequestUpdateUserProfileDTO;
import org.tc.mtracker.user.dto.ResponseUserDTO;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User management endpoints")
@Validated
public class UserController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordService passwordService;

    @Operation(summary = "Update user's profile",
            description = "Updates the user's data by new one.")
    @ApiResponse(
            responseCode = "200",
            description = "User's data updated successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ResponseUserDTO.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseUserDTO> updateMe(
            @Parameter(
                    name = "User profile update DTO",
                    required = false,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @Valid
            @RequestPart(name = "dto", required = false) RequestUpdateUserProfileDTO dto,
            @Parameter(
                    name = "avatar",
                    required = false,
                    content = {
                            @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")),
                    }
            )
            @ValidImage
            @RequestParam(name = "avatar", required = false) MultipartFile avatar,
            @Parameter(hidden = true) Authentication auth
    ) {
        ResponseUserDTO responseUserProfileDTO = userService.updateProfile(dto, avatar, auth.getName());
        return ResponseEntity.ok()
                .body(responseUserProfileDTO);
    }

    @PostMapping(value = "/me/update-email")
    public ResponseEntity<Void> updateEmail(
            @RequestBody RequestUpdateUserEmailDTO dto,
            @Parameter(hidden = true) Authentication auth) {
        emailVerificationService.updateEmail(dto, auth.getName());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Update user's password",
            description = "Updates the user's password by new one.")
    @ApiResponse(
            responseCode = "200",
            description = "Password updated successfully"
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid password format",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @PutMapping(value = "/me/update-password")
    public ResponseEntity<Void> updatePassword(
            @RequestBody @Valid RequestUpdateUserPasswordDTO dto,
            @Parameter(hidden = true) Authentication auth) {
        passwordService.updatePassword(dto, auth.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmailUpdate(token);
        return ResponseEntity.ok().build();
    }

    @ApiResponse(responseCode = "200", description = "User profile returned",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ResponseUserDTO.class)))
    @ApiResponse(responseCode = "400", description = "User not found",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = "User with username 'Alex Noob' not found")))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = "Full authentication is required to access this resource")))
    @ApiResponse(responseCode = "405", description = "Method not allowed",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = "The method is not supported: POST")))
    @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = "Internal error: NullPointerException")))
    @GetMapping("/me")
    public ResponseEntity<ResponseUserDTO> getUserProfile(Authentication auth) {
        return ResponseEntity.ok(userService.getUser(auth.getName()));
    }
}
