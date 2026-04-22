package org.tc.mtracker.unit.category;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.tc.mtracker.category.Category;
import org.tc.mtracker.category.CategoryMapper;
import org.tc.mtracker.category.CategoryRepository;
import org.tc.mtracker.category.CategoryService;
import org.tc.mtracker.category.dto.CategoryResponseDTO;
import org.tc.mtracker.category.dto.CreateCategoryDTO;
import org.tc.mtracker.category.dto.UpdateCategoryDTO;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.support.factory.EntityTestFactory;
import org.tc.mtracker.transaction.TransactionRepository;
import org.tc.mtracker.transaction.recurring.RecurringTransactionRepository;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.exceptions.CategoryAlreadyExistsException;
import org.tc.mtracker.utils.exceptions.CategoryReplacementRequiredException;
import org.tc.mtracker.utils.exceptions.InvalidCategoryReplacementException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private UserService userService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RecurringTransactionRepository recurringTransactionRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void shouldNormalizeBlankFiltersAndLoadAccessibleCategories() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        List<Category> categories = List.of(
                EntityTestFactory.category(1L, null, "Salary", TransactionType.INCOME, CategoryStatus.ACTIVE)
        );
        List<CategoryResponseDTO> response = List.of(
                new CategoryResponseDTO(1L, "Salary", TransactionType.INCOME, CategoryStatus.ACTIVE, "icon")
        );

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findGlobalAndUserCategories(
                eq(user),
                isNull(),
                eq(List.of(TransactionType.values())),
                eq(CategoryStatus.ACTIVE)
        )).thenReturn(categories);
        when(categoryMapper.toListDto(categories)).thenReturn(response);

        List<CategoryResponseDTO> result = categoryService.getCategories("   ", null, false, authentication);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void shouldCreateCategoryWhenNameAndTypeAreUnique() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        CreateCategoryDTO dto = new CreateCategoryDTO("Health", TransactionType.EXPENSE, "heart");
        Category saved = EntityTestFactory.category(3L, user, "Health", TransactionType.EXPENSE, CategoryStatus.ACTIVE);
        CategoryResponseDTO response = new CategoryResponseDTO(3L, "Health", TransactionType.EXPENSE, CategoryStatus.ACTIVE, "heart");

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findAllByNameAndUser(dto.name(), user)).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);
        when(categoryMapper.toDto(saved)).thenReturn(response);

        CategoryResponseDTO result = categoryService.createCategory(dto, authentication);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());

        assertThat(result).isEqualTo(response);
        assertThat(captor.getValue().getName()).isEqualTo("Health");
        assertThat(captor.getValue().getStatus()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void shouldRejectDuplicateCategoryDuringCreate() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        CreateCategoryDTO dto = new CreateCategoryDTO("Salary", TransactionType.INCOME, "coin");
        Category existing = EntityTestFactory.category(1L, user, "Salary", TransactionType.INCOME, CategoryStatus.ACTIVE);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findAllByNameAndUser(dto.name(), user)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> categoryService.createCategory(dto, authentication))
                .isInstanceOf(CategoryAlreadyExistsException.class);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void shouldUpdateOwnedCategory() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Category category = EntityTestFactory.category(3L, user, "Side Project", TransactionType.INCOME, CategoryStatus.ACTIVE);
        UpdateCategoryDTO dto = new UpdateCategoryDTO("Freelance", "briefcase");
        CategoryResponseDTO response = new CategoryResponseDTO(3L, "Freelance", TransactionType.INCOME, CategoryStatus.ACTIVE, "briefcase");

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findOwnedById(3L, user)).thenReturn(Optional.of(category));
        when(categoryRepository.findAllByNameAndUser(dto.name(), user)).thenReturn(List.of());
        when(categoryRepository.save(category)).thenReturn(category);
        when(categoryMapper.toDto(category)).thenReturn(response);

        CategoryResponseDTO result = categoryService.updateCategory(3L, dto, authentication);

        assertThat(result).isEqualTo(response);
        assertThat(category.getName()).isEqualTo("Freelance");
        assertThat(category.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(category.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
    }

    @Test
    void shouldArchiveCategoryOnlyWhenItIsActive() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Category activeCategory = EntityTestFactory.category(3L, user, "Rent", TransactionType.EXPENSE, CategoryStatus.ACTIVE);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findOwnedById(3L, user)).thenReturn(Optional.of(activeCategory));

        categoryService.archiveCategory(3L, authentication);

        assertThat(activeCategory.getStatus()).isEqualTo(CategoryStatus.ARCHIVED);
        verify(categoryRepository).save(activeCategory);
    }

    @Test
    void shouldSkipSavingAlreadyArchivedCategory() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Category archivedCategory = EntityTestFactory.category(3L, user, "Rent", TransactionType.EXPENSE, CategoryStatus.ARCHIVED);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findOwnedById(3L, user)).thenReturn(Optional.of(archivedCategory));

        categoryService.archiveCategory(3L, authentication);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void shouldUnarchiveCategoryOnlyWhenItIsArchived() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Category archivedCategory = EntityTestFactory.category(3L, user, "Rent", TransactionType.EXPENSE, CategoryStatus.ARCHIVED);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findOwnedById(3L, user)).thenReturn(Optional.of(archivedCategory));

        categoryService.unarchiveCategory(3L, authentication);

        assertThat(archivedCategory.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
        verify(categoryRepository).save(archivedCategory);
    }

    @Test
    void shouldDeleteUnusedCategoryWithoutReplacement() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Category category = EntityTestFactory.category(3L, user, "Rent", TransactionType.EXPENSE, CategoryStatus.ACTIVE);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findOwnedById(3L, user)).thenReturn(Optional.of(category));
        when(transactionRepository.countByUserAndCategory(user, category)).thenReturn(0L);
        when(recurringTransactionRepository.countByUserAndCategory(user, category)).thenReturn(0L);

        categoryService.deleteCategory(3L, null, authentication);

        verify(transactionRepository, never()).reassignCategory(any(), any(), any());
        verify(recurringTransactionRepository, never()).reassignCategory(any(), any(), any());
        verify(categoryRepository).delete(category);
    }

    @Test
    void shouldRequireReplacementWhenDeletingUsedCategory() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Category category = EntityTestFactory.category(3L, user, "Rent", TransactionType.EXPENSE, CategoryStatus.ACTIVE);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findOwnedById(3L, user)).thenReturn(Optional.of(category));
        when(transactionRepository.countByUserAndCategory(user, category)).thenReturn(2L);
        when(recurringTransactionRepository.countByUserAndCategory(user, category)).thenReturn(0L);

        assertThatThrownBy(() -> categoryService.deleteCategory(3L, null, authentication))
                .isInstanceOf(CategoryReplacementRequiredException.class)
                .hasMessage("Replacement category is required when category is already used.");

        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void shouldReassignTransactionsAndRecurringTransactionsBeforeDelete() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Category sourceCategory = EntityTestFactory.category(3L, user, "Rent", TransactionType.EXPENSE, CategoryStatus.ACTIVE);
        Category replacementCategory = EntityTestFactory.category(5L, null, "Housing", TransactionType.EXPENSE, CategoryStatus.ACTIVE);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findOwnedById(3L, user)).thenReturn(Optional.of(sourceCategory));
        when(categoryRepository.findAccessibleById(5L, user)).thenReturn(Optional.of(replacementCategory));
        when(transactionRepository.countByUserAndCategory(user, sourceCategory)).thenReturn(2L);
        when(recurringTransactionRepository.countByUserAndCategory(user, sourceCategory)).thenReturn(1L);

        categoryService.deleteCategory(3L, 5L, authentication);

        verify(transactionRepository).reassignCategory(user, sourceCategory, replacementCategory);
        verify(recurringTransactionRepository).reassignCategory(user, sourceCategory, replacementCategory);
        verify(categoryRepository).delete(sourceCategory);
    }

    @Test
    void shouldRejectArchivedReplacementCategoryDuringDelete() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Category sourceCategory = EntityTestFactory.category(3L, user, "Rent", TransactionType.EXPENSE, CategoryStatus.ACTIVE);
        Category replacementCategory = EntityTestFactory.category(5L, null, "Housing", TransactionType.EXPENSE, CategoryStatus.ARCHIVED);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryRepository.findOwnedById(3L, user)).thenReturn(Optional.of(sourceCategory));
        when(transactionRepository.countByUserAndCategory(user, sourceCategory)).thenReturn(1L);
        when(recurringTransactionRepository.countByUserAndCategory(user, sourceCategory)).thenReturn(0L);
        when(categoryRepository.findAccessibleById(5L, user)).thenReturn(Optional.of(replacementCategory));

        assertThatThrownBy(() -> categoryService.deleteCategory(3L, 5L, authentication))
                .isInstanceOf(InvalidCategoryReplacementException.class)
                .hasMessage("Replacement category must be active.");

        verify(categoryRepository, never()).delete(any(Category.class));
    }
}
