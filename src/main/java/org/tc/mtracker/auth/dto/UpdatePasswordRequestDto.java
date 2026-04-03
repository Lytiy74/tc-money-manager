package org.tc.mtracker.auth.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record UpdatePasswordRequestDto(
        @NotBlank @Length(min = 8, max = 256) String currentPassword,
        @NotBlank @Length(min = 8, max = 256) String newPassword,
        @NotBlank @Length(min = 8, max = 256) String confirmNewPassword
) {
}
