package org.tc.mtracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.tc.mtracker.common.enums.MoneyFlowType;

@Schema(description = "Create a new category")
public record CreateCategoryDTO(
        @Schema(description = "Name", example = "Salary")
        @NotEmpty(message = "Name should not be empty")
        String name,

        @Schema(description = "Type", example = "Income")
        @NotNull(message = "Category's type should not be null")
        MoneyFlowType type,

        @Schema(description = "Icon", example = "coin")
        String icon
) {
}
