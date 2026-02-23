package org.tc.mtracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.category.enums.CategoryType;

@Schema(description = "Get Category")
public record CategoryResponseDTO(

        @Schema(description = "Name", example = "Salary")
        String name,

        @Schema(description = "Type", example = "Income")
        CategoryType type,

        @Schema(description = "Status", example = "Active")
        CategoryStatus status,

        @Schema(description = "Icon", example = "coin")
        String icon
) {
}
