package org.tc.mtracker.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionCreateRequestDTO(
        BigDecimal amount,

        Long categoryId,

        LocalDate date,

        String description
) {
}
