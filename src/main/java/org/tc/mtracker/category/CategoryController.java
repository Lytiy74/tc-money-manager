package org.tc.mtracker.category;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.tc.mtracker.category.dto.CategoryResponseDTO;
import org.tc.mtracker.category.dto.CreateCategoryDTO;
import org.tc.mtracker.category.dto.UpdateCategoryDTO;
import org.tc.mtracker.common.enums.TransactionType;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Category Management", description = "Category management endpoints")
@Validated
public class CategoryController  {

    private final CategoryService categoryService;

    @Operation(
            summary = "Get a list of available categories",
            description = "Returns the whole list of user's categories"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved categories",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = CategoryResponseDTO.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters (e.g., invalid CategoryType)",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - User is not authenticated",
                    content = @Content
            )
    })
    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getCategories(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "type", required = false) List<TransactionType> type,
            @RequestParam(value = "archived", defaultValue = "false") boolean archived,
            @Parameter(hidden = true) Authentication auth
    ) {
        return ResponseEntity.ok((categoryService.getCategories(name, type, archived, auth)));
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponseDTO> getCategoryById(
            @PathVariable("categoryId") Long categoryId,
            @Parameter(hidden = true) Authentication auth
    ) {
        return ResponseEntity.ok(categoryService.getCategoryById(categoryId, auth));
    }

    @Operation(
            summary = "Create a new category",
            description = "Creates a custom category for the authenticated user. " +
                    "Returns 409 if a category with the same name and type already exists for this user."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Category created successfully",
                    content = @Content(schema = @Schema(implementation = CategoryResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data or validation failed",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Valid JWT token required",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Category with this name and type already exists",
                    content = @Content
            )
    })
    @PostMapping
    public ResponseEntity<CategoryResponseDTO> createCategory(
            @Valid @RequestBody CreateCategoryDTO dto,
            @Parameter(hidden = true) Authentication auth
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(categoryService.createCategory(dto, auth));
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<CategoryResponseDTO> updateCategory(
            @PathVariable("categoryId") Long categoryId,
            @Valid @RequestBody UpdateCategoryDTO dto,
            @Parameter(hidden = true) Authentication auth
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(categoryId, dto, auth));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable("categoryId") Long categoryId,
            @Parameter(hidden = true) Authentication auth
    ) {
        categoryService.archiveCategory(categoryId, auth);
        return ResponseEntity.noContent().build();
    }
}
