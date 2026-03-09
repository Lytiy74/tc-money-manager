package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tc.mtracker.currency.CurrencyCode;

import java.time.LocalDateTime;

@Schema(description = "User sign up response")
public record AuthResponseDTO(
        @Schema(description = "User's id", example = "123123")
        Long id,

        @Schema(description = "User's full name", example = "Abraham Lincoln")
        String fullName,

        @Schema(description = "User's email", examples = "example@mail.com")
        String email,

        @Schema(description = "User's main currency code", example = "USD")
        CurrencyCode currencyCode,

        @Schema(description = "User's avatar url", example = "https://example.com/avatar.jpg")
        String avatarUrl,

        @Schema(description = "User's activation status", example = "false")
        boolean isActivated,

        @Schema(description = "User's creation date, time", example = "2026-03-09T21:15:54.445851")
        LocalDateTime createdAt
) {
}
