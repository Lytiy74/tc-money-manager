package org.tc.mtracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;

@Schema(description = "Update category")
public record UpdateCategoryDTO(
        @Schema(description = "Name", example = "Salary")
        @NotEmpty(message = "Name should not be empty")
        String name,

        @Schema(description = "Type", example = "INCOME")
        @NotNull(message = "Category's type should not be null")
        TransactionType type,

        @Schema(description = "Icon", example = "coin")
        String icon,

        @Schema(description = "Status", example = "ACTIVE")
        @NotNull(message = "Category's status should not be null")
        CategoryStatus status
) {
}
