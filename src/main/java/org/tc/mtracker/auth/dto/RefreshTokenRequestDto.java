package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for refreshing authentication token")
public record RefreshTokenRequestDto(

        @Schema(description = "Valid refresh token", example = "eyJhbGciOiJIUzI1NiJ9.ey...")
        @NotBlank
        String refreshToken
) {
}
