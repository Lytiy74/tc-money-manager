package org.tc.mtracker.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.tc.mtracker.user.dto.UpdateUserEmailRequestDTO;
import org.tc.mtracker.user.dto.UserProfileResponseDTO;
import org.tc.mtracker.user.dto.UpdateUserProfileRequestDTO;
import org.tc.mtracker.user.dto.UserResponseDTO;
import org.tc.mtracker.utils.image.ValidImage;

@Tag(name = "User Management", description = "User management endpoints")
public interface UserProfileAPI {

    @Operation(summary = "Update user's profile",
            description = "Updates the user's data by new one.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User's data updated successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserProfileResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UserProfileResponseDTO> updateMe(
            @Parameter(
                    name = "User profile update DTO",
                    required = false,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @Valid
            @RequestPart(name = "dto", required = false) UpdateUserProfileRequestDTO dto,
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
    );

    @PostMapping(value = "/me/update-email")
    ResponseEntity<UserProfileResponseDTO> updateEmail(
            @RequestBody UpdateUserEmailRequestDTO dto,
            @Parameter(hidden = true) Authentication auth
    );

    @GetMapping(value = "/verify-email")
    ResponseEntity<Void> verifyEmail(@RequestParam String token);

    @ApiResponses(value ={
            @ApiResponse(responseCode = "200", description = "User profile returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "User not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "User with username 'Alex Noob' not found"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "Full authentication is required to access this resource"))),
            @ApiResponse(responseCode = "405", description = "Method not allowed",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "The method is not supported: POST"))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "Internal error: NullPointerException")))
    })
    @GetMapping(value = "/me")
    ResponseEntity<UserResponseDTO> getUserProfile(Authentication auth);
}
