package org.tc.mtracker.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.tc.mtracker.common.enums.MoneyFlowType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionCreateRequestDTO(

        @DecimalMin(value = "0.00")
        BigDecimal amount,

        MoneyFlowType type,

        @NotNull
        Long categoryId,

        @NotNull
        LocalDate date,

        @Length(max = 256)
        String description
) {
}
