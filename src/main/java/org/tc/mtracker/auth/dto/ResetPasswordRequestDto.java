package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

@Schema(description = "New user's password")
public record ResetPasswordRequestDto(
        @Schema(
                description = "User's password (min 8 chars).",
                example = "Example8",
                format = "password"
        )
        @NotBlank @Length(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\\\\|,.<>/?~`]).{8,72}$",
                message = "Password must contain uppercase, lowercase, and special characters without spaces."
        )
        String password,

        @Schema(
                description = "User's confirmPassword (min 8 chars).",
                example = "Example8",
                format = "password"
        )
        @NotBlank @Length(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\\\\|,.<>/?~`]).{8,72}$",
                message = "Password must contain uppercase, lowercase, and special characters without spaces."
        )
        String confirmPassword
) {
}
