package org.tc.mtracker.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tc.mtracker.category.dto.CategoryResponseDTO;
import org.tc.mtracker.category.dto.CreateCategoryDTO;
import org.tc.mtracker.category.dto.UpdateCategoryDTO;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.transaction.TransactionRepository;
import org.tc.mtracker.transaction.recurring.RecurringTransactionRepository;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.exceptions.CategoryAlreadyExistsException;
import org.tc.mtracker.utils.exceptions.CategoryNotFoundException;
import org.tc.mtracker.utils.exceptions.CategoryReplacementRequiredException;
import org.tc.mtracker.utils.exceptions.InvalidCategoryReplacementException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final UserService userService;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getCategories(String name, List<TransactionType> types, boolean archived, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        String normalizedName = normalizeNameFilter(name);
        List<TransactionType> normalizedTypes = normalizeTypes(types);
        CategoryStatus status = archived ? CategoryStatus.ARCHIVED : CategoryStatus.ACTIVE;

        log.debug("Loading categories for userId={} archived={} name={} types={}",
                currentUser.getId(), archived, normalizedName, normalizedTypes);

        List<Category> categories = categoryRepository.findGlobalAndUserCategories(
                currentUser,
                normalizedName,
                normalizedTypes,
                status
        );

        return categoryMapper.toListDto(categories);
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryById(Long categoryId, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Category category = findAccessibleById(categoryId, currentUser);
        log.debug("Category returned for userId={} categoryId={}", currentUser.getId(), categoryId);
        return categoryMapper.toDto(category);
    }

    public Category findAccessibleById(Long id, User currentUser) {
        return categoryRepository.findAccessibleById(id, currentUser)
                .orElseThrow(() -> {
                    log.warn("Category not found or inaccessible userId={} categoryId={}", currentUser.getId(), id);
                    return new CategoryNotFoundException("Category with id %d not found".formatted(id));
                });
    }

    @Transactional
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
        log.info("Category created userId={} categoryId={} type={}", currentUser.getId(), saved.getId(), saved.getType());

        return categoryMapper.toDto(saved);
    }

    @Transactional
    public CategoryResponseDTO updateCategory(Long categoryId, UpdateCategoryDTO dto, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Category category = findOwnedById(categoryId, currentUser);

        validateDuplicateCategory(dto.name(), category.getType(), currentUser, categoryId);

        category.setName(dto.name());
        category.setIcon(dto.icon());

        Category savedCategory = categoryRepository.save(category);
        log.info("Category updated userId={} categoryId={} type={}",
                currentUser.getId(), savedCategory.getId(), savedCategory.getType());

        return categoryMapper.toDto(savedCategory);
    }

    @Transactional
    public void archiveCategory(Long categoryId, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Category category = findOwnedById(categoryId, currentUser);

        if (category.getStatus() != CategoryStatus.ARCHIVED) {
            category.setStatus(CategoryStatus.ARCHIVED);
            categoryRepository.save(category);
            log.info("Category archived userId={} categoryId={}", currentUser.getId(), categoryId);
        }
    }

    @Transactional
    public void unarchiveCategory(Long categoryId, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Category category = findOwnedById(categoryId, currentUser);

        if (category.getStatus() != CategoryStatus.ACTIVE) {
            category.setStatus(CategoryStatus.ACTIVE);
            categoryRepository.save(category);
            log.info("Category unarchived userId={} categoryId={}", currentUser.getId(), categoryId);
        }
    }

    @Transactional
    public void deleteCategory(Long categoryId, Long replacementCategoryId, Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Category category = findOwnedById(categoryId, currentUser);
        long linkedTransactions = transactionRepository.countByUserAndCategory(currentUser, category);
        long linkedRecurringTransactions = recurringTransactionRepository.countByUserAndCategory(currentUser, category);
        boolean replacementRequired = linkedTransactions > 0 || linkedRecurringTransactions > 0;
        Category replacementCategory = resolveReplacementCategory(category, replacementCategoryId, currentUser, replacementRequired);

        if (linkedTransactions > 0) {
            transactionRepository.reassignCategory(currentUser, category, replacementCategory);
        }
        if (linkedRecurringTransactions > 0) {
            recurringTransactionRepository.reassignCategory(currentUser, category, replacementCategory);
        }

        categoryRepository.delete(category);
        log.info(
                "Category deleted userId={} categoryId={} replacementCategoryId={} transferredTransactions={} transferredRecurringTransactions={}",
                currentUser.getId(),
                categoryId,
                replacementCategory != null ? replacementCategory.getId() : null,
                linkedTransactions,
                linkedRecurringTransactions
        );
    }

    private Category findOwnedById(Long categoryId, User currentUser) {
        return categoryRepository.findOwnedById(categoryId, currentUser)
                .orElseThrow(() -> {
                    log.warn("Owned category not found userId={} categoryId={}", currentUser.getId(), categoryId);
                    return new CategoryNotFoundException("Category with id %d not found".formatted(categoryId));
                });
    }

    private Category resolveReplacementCategory(
            Category category,
            Long replacementCategoryId,
            User currentUser,
            boolean replacementRequired
    ) {
        if (replacementCategoryId == null) {
            if (replacementRequired) {
                log.warn("Category delete rejected userId={} categoryId={} reason=replacement_required", currentUser.getId(), category.getId());
                throw new CategoryReplacementRequiredException("Replacement category is required when category is already used.");
            }
            return null;
        }

        if (replacementCategoryId.equals(category.getId())) {
            throw new InvalidCategoryReplacementException("Replacement category must be different from the deleted category.");
        }

        Category replacementCategory = findAccessibleById(replacementCategoryId, currentUser);
        if (replacementCategory.getType() != category.getType()) {
            throw new InvalidCategoryReplacementException("Replacement category type must match the deleted category type.");
        }
        if (replacementCategory.getStatus() != CategoryStatus.ACTIVE) {
            throw new InvalidCategoryReplacementException("Replacement category must be active.");
        }

        return replacementCategory;
    }

    private void validateDuplicateCategory(String name, TransactionType type, User currentUser, Long excludedCategoryId) {
        List<Category> categoriesWithSameName = categoryRepository.findAllByNameAndUser(name, currentUser);
        boolean isDuplicate = categoriesWithSameName.stream()
                .anyMatch(category -> type.equals(category.getType())
                        && (excludedCategoryId == null || !excludedCategoryId.equals(category.getId())));

        if (isDuplicate) {
            log.warn("Category rejected as duplicate userId={} name={} type={} excludedCategoryId={}",
                    currentUser.getId(), name, type, excludedCategoryId);
            throw new CategoryAlreadyExistsException("Category with this name and type already exists.");
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
