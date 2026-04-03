package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

@Schema(description = "New user's password")
public record ResetPasswordRequestDto(
        @Schema(
                description = "User's password (min 8 chars).",
                example = "Example8",
                format = "password"
        )
        @NotBlank @Length(min = 8, max = 256) String password,

        @Schema(
                description = "User's confirmPassword (min 8 chars).",
                example = "Example8",
                format = "password"
        )
        @NotBlank @Length(min = 8, max = 256) String confirmPassword
) {
}
