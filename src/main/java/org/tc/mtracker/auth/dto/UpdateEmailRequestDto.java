package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Update user email request")
public record UpdateEmailRequestDto(
        @Schema(description = "New email address")
        String email
) {
}
