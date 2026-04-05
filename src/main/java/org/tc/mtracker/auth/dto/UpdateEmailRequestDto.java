package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

@Schema(description = "Update user email request")
public record UpdateEmailRequestDto(
        @Schema(description = "New email address")
        @Email
        String email
) {
}
