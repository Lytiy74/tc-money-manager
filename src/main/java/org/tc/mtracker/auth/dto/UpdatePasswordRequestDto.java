package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

@Schema(description = "Update current user's password request")
public record UpdatePasswordRequestDto(
        @Schema(
                description = "Current password of the authenticated user.",
                example = "CurrentPass!1",
                format = "password"
        )
        @NotBlank(message = "Current password is required.")
        @Length(min = 8, max = 72, message = "Current password must be between 8 and 72 characters.")
        String currentPassword,

        @Schema(
                description = "New password. Must contain at least one uppercase letter, one lowercase letter, " +
                        "one special character from !@#$%^&*, and must not contain spaces.",
                example = "NewStrongPass!",
                format = "password"
        )
        @NotBlank(message = "New password is required.")
        @Length(min = 8, max = 72, message = "New password must be between 8 and 72 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\\\\|,.<>/?~`])\\S{8,72}$",
                message = "New password must contain uppercase, lowercase, and special characters without spaces."
        )
        String newPassword,

        @Schema(
                description = "Confirmation of the new password. Must match the new password.",
                example = "NewStrongPass!",
                format = "password"
        )
        @NotBlank(message = "Password confirmation is required.")
        @Length(min = 8, max = 72, message = "Password confirmation must be between 8 and 72 characters.")
        String confirmNewPassword
) {
}
