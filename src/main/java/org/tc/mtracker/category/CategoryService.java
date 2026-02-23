package org.tc.mtracker.category;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.tc.mtracker.category.dto.CategoryResponseDTO;
import org.tc.mtracker.category.enums.CategoryType;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final UserService userService;

    public List<Category> getCategories(String name, List<CategoryType> types, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);

        return categoryRepository.findGlobalAndUserCategories(
                currentUser,
                name,
                types
        );
    }
}