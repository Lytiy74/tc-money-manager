package org.tc.mtracker.integration.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.tc.mtracker.category.CategoryRepository;
import org.tc.mtracker.category.dto.CreateCategoryDTO;
import org.tc.mtracker.category.dto.UpdateCategoryDTO;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.support.base.BaseApiIntegrationTest;
import org.tc.mtracker.transaction.TransactionRepository;
import org.tc.mtracker.user.User;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class CategoryApiTest extends BaseApiIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void shouldReturnGlobalAndOwnedCategories() {
        User currentUser = fixtures.createUser("categories@example.com");
        User otherUser = fixtures.createUser("other@example.com");
        fixtures.createGlobalCategory("Salary", TransactionType.INCOME);
        fixtures.createGlobalCategory("Rent", TransactionType.EXPENSE);
        fixtures.createUserCategory(currentUser, "Side Project", TransactionType.INCOME);
        fixtures.createCategory(currentUser, "Archived Food", TransactionType.EXPENSE, CategoryStatus.ARCHIVED, "archive");
        fixtures.createUserCategory(otherUser, "Hidden", TransactionType.EXPENSE);

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/categories")
                        .queryParam("type", TransactionType.INCOME, TransactionType.EXPENSE)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authHeader(currentUser))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3);
    }

    @Test
    void shouldFilterArchivedCategories() {
        User currentUser = fixtures.createUser("categories@example.com");
        fixtures.createUserCategory(currentUser, "Active", TransactionType.EXPENSE);
        fixtures.createCategory(currentUser, "Archived Food", TransactionType.EXPENSE, CategoryStatus.ARCHIVED, "archive");

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/categories")
                        .queryParam("type", TransactionType.EXPENSE)
                        .queryParam("archived", true)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authHeader(currentUser))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("Archived Food");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "salary", "salary!", "salary", "SALARY", "hafjykoyawewrryqbtuqgvdsg"})
    void shouldCreateCategory(String categoryName) {
        User user = fixtures.createUser("create-category@example.com");

        restTestClient.post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(new CreateCategoryDTO(categoryName, TransactionType.EXPENSE, "heart"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo(categoryName)
                .jsonPath("$.type").isEqualTo("EXPENSE");

        assertThat(categoryRepository.findAll())
                .extracting("name")
                .contains(categoryName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "hafjykoyawewrryqbtuqgvdsg5"})
    @NullSource
    void shouldRejectCreateCategoryWithInvalidName(String invalidName) {
        User user = fixtures.createUser("create-category@example.com");

        restTestClient.post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(new CreateCategoryDTO(invalidName, TransactionType.EXPENSE, "heart"))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(categoryRepository.findAll())
                .extracting("name")
                .doesNotContain(invalidName);
    }

    @Test
    void shouldRejectDuplicateCategoryNameAndTypeForSameVisibilityScope() {
        User user = fixtures.createUser("duplicate-category@example.com");
        fixtures.createGlobalCategory("Salary", TransactionType.INCOME);

        restTestClient.post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(new CreateCategoryDTO("Salary", TransactionType.INCOME, "coin"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "salary", "salary!", "salary", "SALARY", "hafjykoyawewrryqbtuqgvdsg"})
    void shouldUpdateOwnedCategory(String categoryName) {
        User user = fixtures.createUser("update-category@example.com");
        var category = fixtures.createUserCategory(user, "Freelance", TransactionType.INCOME);

        restTestClient.put()
                .uri("/api/v1/categories/{id}", category.getId())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(new UpdateCategoryDTO(categoryName, TransactionType.INCOME, "briefcase"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo(categoryName);

        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getName()).isEqualTo(categoryName);
    }

    @Test
    void shouldArchiveCategory() {
        User user = fixtures.createUser("delete-category@example.com");
        var category = fixtures.createUserCategory(user, "Travel", TransactionType.EXPENSE);

        restTestClient.patch()
                .uri("/api/v1/categories/{id}/archive", category.getId())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getStatus()).isEqualTo(CategoryStatus.ARCHIVED);
    }

    @Test
    void shouldUnarchiveCategory() {
        User user = fixtures.createUser("unarchive-category@example.com");
        var category = fixtures.createCategory(user, "Travel", TransactionType.EXPENSE, CategoryStatus.ARCHIVED, "icon");

        restTestClient.patch()
                .uri("/api/v1/categories/{id}/unarchive", category.getId())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getStatus()).isEqualTo(CategoryStatus.ACTIVE);
    }

    @Test
    void shouldDeleteUnusedCategory() {
        User user = fixtures.createUser("delete-unused-category@example.com");
        var category = fixtures.createUserCategory(user, "Travel", TransactionType.EXPENSE);

        restTestClient.delete()
                .uri("/api/v1/categories/{id}", category.getId())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
    }

    @Test
    void shouldRequireReplacementToDeleteUsedCategory() {
        User user = fixtures.createUser("delete-used-category@example.com");
        var category = fixtures.createUserCategory(user, "Travel", TransactionType.EXPENSE);
        fixtures.createTransaction(
                user,
                user.getDefaultAccount(),
                category,
                java.math.BigDecimal.TEN,
                TransactionType.EXPENSE,
                java.time.LocalDate.of(2026, 4, 1),
                "Flight"
        );

        restTestClient.delete()
                .uri("/api/v1/categories/{id}", category.getId())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("category_replacement_required");
    }

    @Test
    void shouldTransferTransactionsAndDeleteCategory() {
        User user = fixtures.createUser("delete-transfer-category@example.com");
        var sourceCategory = fixtures.createUserCategory(user, "Travel", TransactionType.EXPENSE);
        var replacementCategory = fixtures.createUserCategory(user, "Vacation", TransactionType.EXPENSE);
        var transaction = fixtures.createTransaction(
                user,
                user.getDefaultAccount(),
                sourceCategory,
                java.math.BigDecimal.TEN,
                TransactionType.EXPENSE,
                java.time.LocalDate.of(2026, 4, 1),
                "Flight"
        );

        restTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/categories/{id}")
                        .queryParam("replacementCategoryId", replacementCategory.getId())
                        .build(sourceCategory.getId()))
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(categoryRepository.findById(sourceCategory.getId())).isEmpty();
        assertThat(transactionRepository.findById(transaction.getId()).orElseThrow().getCategory().getId())
                .isEqualTo(replacementCategory.getId());
    }
}
