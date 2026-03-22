package org.tc.mtracker.category;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.tc.mtracker.category.dto.CategoryResponseDTO;
import org.tc.mtracker.category.dto.CreateCategoryDTO;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.exceptions.CategoryAlreadyExistsException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final UserService userService;

    public List<CategoryResponseDTO> getCategories(String name, List<TransactionType> types, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);

        List<Category> categories = categoryRepository.findGlobalAndUserCategories(
                currentUser,
                name,
                types
        );

        return categoryMapper.toListDto(categories);
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id).orElseThrow();
    }

    public CategoryResponseDTO createCategory(CreateCategoryDTO dto, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);

        Optional<Category> category = categoryRepository.findByNameAndUser(dto.name(), currentUser);

        if (category.isPresent() && dto.type().equals(category.get().getType())) {
            throw new CategoryAlreadyExistsException("This category is already exists. Please enter another name or select another type");
        }

        Category newCategory = Category.builder()
                .name(dto.name())
                .type(dto.type())
                .icon(dto.icon())
                .status(CategoryStatus.ACTIVE)
                .build();

        Category saved = categoryRepository.save(newCategory);

        return categoryMapper.toDto(saved);
    }
}