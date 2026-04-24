package org.tc.mtracker.integration.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.tc.mtracker.account.AccountRepository;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.support.base.BaseApiIntegrationTest;
import org.tc.mtracker.support.factory.MultipartTestResourceFactory;
import org.tc.mtracker.transaction.ReceiptImage;
import org.tc.mtracker.transaction.Transaction;
import org.tc.mtracker.transaction.TransactionRepository;
import org.tc.mtracker.transaction.dto.TransactionCreateRequestDTO;
import org.tc.mtracker.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("integration")
class TransactionApiTest extends BaseApiIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static TransactionCreateRequestDTO createRequest(
            BigDecimal amount,
            TransactionType type,
            Long categoryId,
            LocalDate date,
            String description,
            Long accountId
    ) {
        return new TransactionCreateRequestDTO(
                amount,
                type,
                categoryId,
                date,
                description,
                accountId
        );
    }

    private static MultipartBodyBuilder createMultipartRequest(TransactionCreateRequestDTO request) {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("dto", request, MediaType.APPLICATION_JSON);
        return parts;
    }

    private static MultipartBodyBuilder createMultipartRequest(
            TransactionCreateRequestDTO request,
            ByteArrayResource receipt,
            MediaType receiptMediaType
    ) {
        MultipartBodyBuilder parts = createMultipartRequest(request);
        parts.part("receipts", receipt, receiptMediaType);
        return parts;
    }

    @ParameterizedTest
    @CsvSource({"0.01", "999999999999.99"})
    void shouldCreateTransactionWithoutReceiptsAndUpdateBalance(BigDecimal amount) {
        User user = fixtures.createUser("transactions@example.com");
        var category = fixtures.createGlobalCategory("Salary", TransactionType.INCOME);
        MultipartBodyBuilder parts = createMultipartRequest(createRequest(
                amount,
                TransactionType.INCOME,
                category.getId(),
                LocalDate.of(2026, 4, 1),
                "Salary",
                null
        ));

        restTestClient.post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.accountId").isEqualTo(user.getDefaultAccount().getId())
                .jsonPath("$.receiptsUrls.length()").isEqualTo(0);

        assertThat(accountRepository.findById(user.getDefaultAccount().getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(amount.toString());
        verifyNoInteractions(s3Service);
    }

    @ParameterizedTest
    @CsvSource({"0.00", "-100.00", "9999999999999.99"})
    void shouldRejectTransactionCreateWhenAmountInvalid(BigDecimal amount) {
        User user = fixtures.createUser("transactions@example.com");
        var category = fixtures.createGlobalCategory("Salary", TransactionType.INCOME);
        MultipartBodyBuilder parts = createMultipartRequest(createRequest(
                amount,
                TransactionType.INCOME,
                category.getId(),
                LocalDate.of(2026, 4, 1),
                "Salary",
                null
        ));

        restTestClient.post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(transactionRepository.findAll()).isEmpty();
        verifyNoInteractions(s3Service);
    }

    @Test
    void shouldCreateTransactionWithReceipt() {
        User user = fixtures.createUser("receipts@example.com");
        var category = fixtures.createGlobalCategory("Salary", TransactionType.INCOME);
        when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://test-bucket.local/receipt.jpg");

        MultipartBodyBuilder parts = createMultipartRequest(
                createRequest(
                        new BigDecimal("15.00"),
                        TransactionType.INCOME,
                        category.getId(),
                        LocalDate.of(2026, 4, 1),
                        "Salary",
                        null
                ),
                MultipartTestResourceFactory.jpegImage("receipt.jpg"),
                MediaType.IMAGE_JPEG
        );

        restTestClient.post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.receiptsUrls.length()").isEqualTo(1)
                .jsonPath("$.receiptsUrls[0]").isEqualTo("https://test-bucket.local/receipt.jpg");

        verify(s3Service).saveFile(anyString(), any());
        verify(s3Service).generatePresignedUrl(anyString());
    }

    @Test
    void shouldCreateTransactionWithWebpReceipt() {
        User user = fixtures.createUser("receipts-webp@example.com");
        var category = fixtures.createGlobalCategory("Salary", TransactionType.INCOME);
        when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://test-bucket.local/receipt.webp");

        MultipartBodyBuilder parts = createMultipartRequest(
                createRequest(
                        new BigDecimal("15.00"),
                        TransactionType.INCOME,
                        category.getId(),
                        LocalDate.of(2026, 4, 1),
                        "Salary",
                        null
                ),
                MultipartTestResourceFactory.webpImage("receipt.webp"),
                MediaType.parseMediaType("image/webp")
        );

        restTestClient.post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.receiptsUrls.length()").isEqualTo(1)
                .jsonPath("$.receiptsUrls[0]").isEqualTo("https://test-bucket.local/receipt.webp");

        verify(s3Service).saveFile(anyString(), any());
        verify(s3Service).generatePresignedUrl(anyString());
    }

    @Test
    void shouldRejectArchivedCategory() {
        User user = fixtures.createUser("archived-category@example.com");
        var category = fixtures.createCategory(user, "Archived", TransactionType.INCOME, CategoryStatus.ARCHIVED, "archive");
        MultipartBodyBuilder parts = createMultipartRequest(createRequest(
                new BigDecimal("15.00"),
                TransactionType.INCOME,
                category.getId(),
                LocalDate.of(2026, 4, 1),
                "Salary",
                null
        ));

        restTestClient.post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(s3Service);
    }

    @Test
    void shouldFilterTransactions() {
        User user = fixtures.createUser("filters@example.com");
        var salary = fixtures.createUserCategory(user, "Salary", TransactionType.INCOME);
        var groceries = fixtures.createUserCategory(user, "Groceries", TransactionType.EXPENSE);
        var savings = fixtures.createAccount(user, BigDecimal.ZERO);

        fixtures.createTransaction(user, user.getDefaultAccount(), salary, new BigDecimal("200.00"), TransactionType.INCOME, LocalDate.of(2026, 3, 10), "Salary");
        Transaction expected = fixtures.createTransaction(user, savings, groceries, new BigDecimal("40.00"), TransactionType.EXPENSE, LocalDate.of(2026, 4, 10), "Groceries");

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/transactions")
                        .queryParam("accountId", savings.getId())
                        .queryParam("categoryId", groceries.getId())
                        .queryParam("type", TransactionType.EXPENSE)
                        .queryParam("dateFrom", "2026-04-01")
                        .queryParam("dateTo", "2026-04-30")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(expected.getId())
                .jsonPath("$[0].amount").isEqualTo(40.00);
    }

    @Test
    void shouldFilterTransactionsByArchivedCategory() {
        User user = fixtures.createUser("filters-archived@example.com");
        var archivedCategory = fixtures.createCategory(user, "Archived Groceries", TransactionType.EXPENSE, CategoryStatus.ARCHIVED, "archive");
        Transaction expected = fixtures.createTransaction(
                user,
                user.getDefaultAccount(),
                archivedCategory,
                new BigDecimal("25.00"),
                TransactionType.EXPENSE,
                LocalDate.of(2026, 4, 10),
                "Groceries"
        );

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/transactions")
                        .queryParam("categoryId", archivedCategory.getId())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(expected.getId())
                .jsonPath("$[0].amount").isEqualTo(25.00);
    }

    @Test
    void shouldRejectTransactionCreationWithInvalidAmount() {
        User user = fixtures.createUser("user@example.com");
        var category = fixtures.createGlobalCategory("Salary", TransactionType.INCOME);
        MultipartBodyBuilder parts = createMultipartRequest(createRequest(
                new BigDecimal("0.00"),
                TransactionType.INCOME,
                category.getId(),
                LocalDate.of(2026, 4, 1),
                "Salary",
                null
        ));

        restTestClient.post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldRejectFutureOneTimeTransactionCreate() {
        User user = fixtures.createUser("recurring@example.com");
        var category = fixtures.createGlobalCategory("Salary", TransactionType.INCOME);
        MultipartBodyBuilder parts = createMultipartRequest(createRequest(
                new BigDecimal("15.00"),
                TransactionType.INCOME,
                category.getId(),
                LocalDate.now().plusDays(1),
                "Salary",
                null
        ));

        restTestClient.post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(parts.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("invalid_transaction_date");
    }

    @Test
    void shouldRejectFutureOneTimeTransactionUpdate() {
        User user = fixtures.createUser("update-future-transaction@example.com", true, new BigDecimal("100.00"));
        var category = fixtures.createUserCategory(user, "Groceries", TransactionType.EXPENSE);
        Transaction transaction = fixtures.createTransaction(
                user,
                user.getDefaultAccount(),
                category,
                new BigDecimal("30.00"),
                TransactionType.EXPENSE,
                LocalDate.of(2026, 4, 1),
                "Groceries"
        );

        restTestClient.put()
                .uri("/api/v1/transactions/{id}", transaction.getId())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(createRequest(
                        new BigDecimal("50.00"),
                        TransactionType.EXPENSE,
                        category.getId(),
                        LocalDate.now().plusDays(1),
                        "Updated groceries",
                        user.getDefaultAccount().getId()
                ))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("invalid_transaction_date");
    }

    @Test
    void shouldUpdateTransactionAndRecalculateBalances() {
        User user = fixtures.createUser("update-transaction@example.com", true, new BigDecimal("100.00"));
        var category = fixtures.createUserCategory(user, "Groceries", TransactionType.EXPENSE);
        var savings = fixtures.createAccount(user, new BigDecimal("20.00"));
        Transaction transaction = fixtures.createTransaction(
                user,
                user.getDefaultAccount(),
                category,
                new BigDecimal("30.00"),
                TransactionType.EXPENSE,
                LocalDate.of(2026, 4, 1),
                "Groceries"
        );

        restTestClient.put()
                .uri("/api/v1/transactions/{id}", transaction.getId())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .body(createRequest(
                        new BigDecimal("50.00"),
                        TransactionType.EXPENSE,
                        category.getId(),
                        LocalDate.of(2026, 4, 2),
                        "Updated groceries",
                        savings.getId()
                        
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountId").isEqualTo(savings.getId());

        assertThat(accountRepository.findById(user.getDefaultAccount().getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
        assertThat(accountRepository.findById(savings.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("-30.00");
    }

    @Test
    void shouldDeleteTransactionAndRollbackBalance() {
        User user = fixtures.createUser("delete-transaction@example.com");
        var category = fixtures.createUserCategory(user, "Salary", TransactionType.INCOME);
        Transaction transaction = fixtures.createTransaction(
                user,
                user.getDefaultAccount(),
                category,
                new BigDecimal("30.00"),
                TransactionType.INCOME,
                LocalDate.of(2026, 4, 1),
                "Salary"
        );
        UUID receiptId = UUID.randomUUID();
        transaction.getReceipts().add(new ReceiptImage(receiptId, transaction));
        transactionRepository.saveAndFlush(transaction);

        restTestClient.delete()
                .uri("/api/v1/transactions/{id}", transaction.getId())
                .header(HttpHeaders.AUTHORIZATION, authHeader(user))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(accountRepository.findById(user.getDefaultAccount().getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.00");
        assertThat(transactionRepository.findById(transaction.getId())).isEmpty();
        verify(s3Service).deleteFile("receipts/" + receiptId);
    }
}
