package org.tc.mtracker.category;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tc.mtracker.category.dto.CategoryResponseDTO;
import org.tc.mtracker.category.enums.CategoryType;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Category Management", description = "Category management endpoints")
@Validated
public class CategoryController  {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    @Operation(
            summary = "Get a list of available categories",
            description = "Returns the whole list of user's categories"
    )
    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getCategories(
            @RequestParam("name") String name,
            @RequestParam("type") List<CategoryType> type,
            @Parameter(hidden = true) Authentication auth
    ) {
        List<Category> foundCategories = categoryService.getCategories(name, type, auth);

        return ResponseEntity.ok(categoryMapper.toListDto(foundCategories));
    }
}
