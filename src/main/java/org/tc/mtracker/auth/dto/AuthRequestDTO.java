package org.tc.mtracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;
import org.tc.mtracker.currency.CurrencyCode;

@Schema(description = "User sign up request")
public record AuthRequestDTO(
        @Schema(description = "User's email address", example = "example@mail.com")
        @NotBlank @Email String email,

        @Schema(
                description = "User's password (min 8 chars) at least one uppercase character, " +
                        "at least one lowercase character, at least one digit, without spaces.",
                example = "Example8!",
                format = "password"
        )
        @NotBlank @Length(min = 8, max = 256)
        @Pattern(regexp = "^((?=\\S*?[A-Z])(?=\\S*?[a-z])(?=\\S*?[0-9]).{8,})\\S$")
        String password,

        @Schema(description = "User's full name", example = "Abraham Lincoln")
        @NotBlank @Length(min = 1, max = 128) String fullName,

        @Schema(description = "User's main currency (ISO 4217)", example = "USD")
        @NotNull CurrencyCode currencyCode
) {
}
