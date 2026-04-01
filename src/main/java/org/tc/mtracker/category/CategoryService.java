package org.tc.mtracker.category;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.tc.mtracker.category.dto.CategoryResponseDTO;
import org.tc.mtracker.category.dto.CreateCategoryDTO;
import org.tc.mtracker.category.dto.UpdateCategoryDTO;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.exceptions.CategoryAlreadyExistsException;
import org.tc.mtracker.utils.exceptions.CategoryNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final UserService userService;

    public List<CategoryResponseDTO> getCategories(String name, List<TransactionType> types, boolean archived, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        String normalizedName = normalizeNameFilter(name);
        List<TransactionType> normalizedTypes = normalizeTypes(types);
        CategoryStatus status = archived ? CategoryStatus.ARCHIVED : CategoryStatus.ACTIVE;

        List<Category> categories = categoryRepository.findGlobalAndUserCategories(
                currentUser,
                normalizedName,
                normalizedTypes,
                status
        );

        return categoryMapper.toListDto(categories);
    }

    public CategoryResponseDTO getCategoryById(Long categoryId, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Category category = findAccessibleById(categoryId, currentUser);
        return categoryMapper.toDto(category);
    }

    public Category findAccessibleById(Long id, User currentUser) {
        return categoryRepository.findAccessibleById(id, currentUser)
                .orElseThrow(() -> new CategoryNotFoundException("Category with id %d not found".formatted(id)));
    }

    public CategoryResponseDTO createCategory(CreateCategoryDTO dto, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);

        validateDuplicateCategory(dto.name(), dto.type(), currentUser, null);

        Category newCategory = Category.builder()
                .name(dto.name())
                .type(dto.type())
                .icon(dto.icon())
                .status(CategoryStatus.ACTIVE)
                .user(currentUser)
                .build();

        Category saved = categoryRepository.save(newCategory);

        return categoryMapper.toDto(saved);
    }

    public CategoryResponseDTO updateCategory(Long categoryId, UpdateCategoryDTO dto, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Category category = findOwnedById(categoryId, currentUser);

        validateDuplicateCategory(dto.name(), dto.type(), currentUser, categoryId);

        category.setName(dto.name());
        category.setType(dto.type());
        category.setIcon(dto.icon());
        category.setStatus(dto.status());

        Category savedCategory = categoryRepository.save(category);

        return categoryMapper.toDto(savedCategory);
    }

    public void archiveCategory(Long categoryId, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Category category = findOwnedById(categoryId, currentUser);

        if (category.getStatus() != CategoryStatus.ARCHIVED) {
            category.setStatus(CategoryStatus.ARCHIVED);
            categoryRepository.save(category);
        }
    }

    private Category findOwnedById(Long categoryId, User currentUser) {
        return categoryRepository.findOwnedById(categoryId, currentUser)
                .orElseThrow(() -> new CategoryNotFoundException("Category with id %d not found".formatted(categoryId)));
    }

    private void validateDuplicateCategory(String name, TransactionType type, User currentUser, Long excludedCategoryId) {
        List<Category> categoriesWithSameName = categoryRepository.findAllByNameAndUser(name, currentUser);
        boolean isDuplicate = categoriesWithSameName.stream()
                .anyMatch(category -> type.equals(category.getType())
                        && (excludedCategoryId == null || !excludedCategoryId.equals(category.getId())));

        if (isDuplicate) {
            throw new CategoryAlreadyExistsException("This category is already exists. Please enter another name or select another type");
        }
    }

    private static String normalizeNameFilter(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim();
    }

    private static List<TransactionType> normalizeTypes(List<TransactionType> types) {
        if (types == null || types.isEmpty()) {
            return List.of(TransactionType.values());
        }
        return types;
    }
}
