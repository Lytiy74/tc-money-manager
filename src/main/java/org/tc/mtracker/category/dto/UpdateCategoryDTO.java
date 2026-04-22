package org.tc.mtracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.tc.mtracker.common.enums.TransactionType;

@Schema(description = "Update category")
public record UpdateCategoryDTO(
        @Schema(description = "Name", example = "Salary")
        @NotEmpty(message = "Name should not be empty")
        @Length(min = 1, max = 25, message = "Name should be between 1 and 25 characters")
        @NotNull(message = "Category's name should not be null")
        String name,

        @Schema(description = "Type", example = "INCOME")
        @NotNull(message = "Category's type should not be null")
        TransactionType type,

        @Schema(description = "Icon", example = "coin")
        String icon
) {
}
