package org.tc.mtracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.tc.mtracker.common.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Create or update a one-time transaction. Date can be in the past or today, but not in the future.")
public record TransactionCreateRequestDTO(

        @NotNull
        @DecimalMin(value = "0.01")
        @DecimalMax(value = "999999999999.99")
        @Schema(description = "Transaction amount, min 0.01 max 999_999_999_999.99", example = "125.50")
        BigDecimal amount,

        @NotNull
        @Schema(description = "Transaction type", example = "EXPENSE")
        TransactionType type,

        @NotNull
        @Schema(description = "Category ID", example = "4")
        Long categoryId,

        @NotNull
        @Schema(description = "Transaction date. For one-time transactions only past or today is allowed.", example = "2026-04-17")
        LocalDate date,

        @Length(max = 255)
        @Schema(description = "Optional transaction description", example = "Groceries")
        String description,

        @Schema(description = "Optional account ID. If omitted, the default account is used.", example = "1")
        Long accountId
) {
}
