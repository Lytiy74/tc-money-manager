package org.tc.mtracker.transaction.dto;

import org.tc.mtracker.category.dto.CategoryResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionResponseDTO(
        BigDecimal amount,
        CategoryResponseDTO category,
        String description,
        String type,
        List<String> receiptsUrls,
        LocalDate date,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

}
