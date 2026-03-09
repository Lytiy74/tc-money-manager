package org.tc.mtracker.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import org.tc.mtracker.currency.CurrencyCode;

public record RequestUpdateUserProfileDTO(
        @Schema(description = "User's full name", example = "Abraham Lincoln")
        @Length(min = 1, max = 128)
        String fullName,

        @Schema(description = "User's main currency (ISO 4217)", example = "USD")
        CurrencyCode currencyCode
) {
}
