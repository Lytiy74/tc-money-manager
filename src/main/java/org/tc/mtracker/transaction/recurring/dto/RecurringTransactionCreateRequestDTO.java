package org.tc.mtracker.transaction.recurring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.transaction.recurring.enums.IntervalUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Create a recurring transaction. Start date can be today or in the future. Recurrence is calculated from the provided start date.")
public record RecurringTransactionCreateRequestDTO(

        @NotNull
        @DecimalMin(value = "0.01")
        @DecimalMax(value = "999999999999.99")
        @Schema(description = "Transaction amount, min 0.01 max 999_999_999_999.99", example = "2500.00")
        BigDecimal amount,

        @NotNull
        @Schema(description = "Transaction type", example = "INCOME")
        TransactionType type,

        @NotNull
        @Schema(description = "Category ID", example = "4")
        Long categoryId,

        @NotNull
        @Schema(description = "Recurring start date. Only today or future is allowed.", example = "2026-05-01")
        LocalDate date,

        @Length(max = 255)
        @Schema(description = "Optional recurring transaction description", example = "Monthly salary")
        String description,

        @Schema(description = "Optional account ID. If omitted, the default account is used.", example = "1")
        Long accountId,

        @NotNull
        @Schema(description = "Recurring interval", example = "MONTHLY")
        IntervalUnit intervalUnit
) {
}
