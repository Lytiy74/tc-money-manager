package org.tc.mtracker.account.dto;

import java.math.BigDecimal;

public record AccountResponseDTO(
        Long id,
        BigDecimal balance
) {
}
