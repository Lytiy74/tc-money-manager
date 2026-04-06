package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Update user email request")
public record UpdateEmailRequestDto(
        @Schema(description = "New email address")
        @Email
        @NotBlank
        String email
) {
}
