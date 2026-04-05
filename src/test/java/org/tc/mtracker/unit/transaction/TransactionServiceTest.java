package org.tc.mtracker.unit.transaction;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.tc.mtracker.account.Account;
import org.tc.mtracker.account.AccountRepository;
import org.tc.mtracker.category.Category;
import org.tc.mtracker.category.CategoryService;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.support.factory.EntityTestFactory;
import org.tc.mtracker.transaction.Transaction;
import org.tc.mtracker.transaction.TransactionRepository;
import org.tc.mtracker.transaction.TransactionService;
import org.tc.mtracker.transaction.dto.TransactionCreateRequestDTO;
import org.tc.mtracker.transaction.dto.TransactionMapper;
import org.tc.mtracker.transaction.dto.TransactionResponseDTO;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.S3Service;
import org.tc.mtracker.utils.exceptions.CategoryIsNotActiveException;
import org.tc.mtracker.utils.exceptions.MoneyFlowTypeMismatchException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private UserService userService;

    @Mock
    private S3Service s3Service;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void shouldUseDefaultAccountAndIncreaseBalanceForIncomeTransaction() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Account defaultAccount = EntityTestFactory.account(1L, user, new BigDecimal("10.00"));
        Category category = EntityTestFactory.category(4L, user, "Salary", TransactionType.INCOME, CategoryStatus.ACTIVE);
        TransactionCreateRequestDTO dto = new TransactionCreateRequestDTO(
                new BigDecimal("15.50"),
                TransactionType.INCOME,
                4L,
                LocalDate.of(2026, 4, 1),
                "Salary",
                null
        );
        Transaction transaction = EntityTestFactory.transaction(null, user, null, null, dto.type(), dto.amount(), dto.date());
        TransactionResponseDTO response = new TransactionResponseDTO(
                10L,
                1L,
                dto.amount(),
                null,
                dto.description(),
                dto.type(),
                List.of(),
                dto.date(),
                null,
                null
        );
        EntityTestFactory.linkDefaultAccount(user, defaultAccount);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(transactionMapper.toEntity(dto, user)).thenReturn(transaction);
        when(categoryService.findAccessibleById(dto.categoryId(), user)).thenReturn(category);
        when(transactionRepository.save(transaction)).thenReturn(transaction);
        when(transactionMapper.toDto(transaction, List.of())).thenReturn(response);

        TransactionResponseDTO result = transactionService.createTransaction(authentication, dto, List.of());

        assertThat(result).isEqualTo(response);
        assertThat(defaultAccount.getBalance()).isEqualByComparingTo("25.50");
        assertThat(transaction.getUser()).isEqualTo(user);
        assertThat(transaction.getAccount()).isEqualTo(defaultAccount);
        assertThat(transaction.getCategory()).isEqualTo(category);
        verifyNoInteractions(s3Service);
    }

    @Test
    void shouldUploadReceiptsAndReturnPresignedUrlsDuringCreate() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Account defaultAccount = EntityTestFactory.account(1L, user, BigDecimal.ZERO);
        Category category = EntityTestFactory.category(4L, user, "Salary", TransactionType.INCOME, CategoryStatus.ACTIVE);
        TransactionCreateRequestDTO dto = new TransactionCreateRequestDTO(
                new BigDecimal("15.50"),
                TransactionType.INCOME,
                4L,
                LocalDate.of(2026, 4, 1),
                "Salary",
                null
        );
        Transaction transaction = EntityTestFactory.transaction(null, user, null, null, dto.type(), dto.amount(), dto.date());
        MockMultipartFile receipt = new MockMultipartFile("receipts", "receipt.jpg", "image/jpeg", "receipt".getBytes());
        TransactionResponseDTO response = new TransactionResponseDTO(
                10L,
                1L,
                dto.amount(),
                null,
                dto.description(),
                dto.type(),
                List.of("https://test-bucket.local/receipt-1"),
                dto.date(),
                null,
                null
        );
        EntityTestFactory.linkDefaultAccount(user, defaultAccount);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(transactionMapper.toEntity(dto, user)).thenReturn(transaction);
        when(categoryService.findAccessibleById(dto.categoryId(), user)).thenReturn(category);
        when(transactionRepository.save(transaction)).thenReturn(transaction);
        when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://test-bucket.local/receipt-1");
        when(transactionMapper.toDto(eq(transaction), eq(List.of("https://test-bucket.local/receipt-1")))).thenReturn(response);

        TransactionResponseDTO result = transactionService.createTransaction(authentication, dto, List.of(receipt));

        assertThat(result).isEqualTo(response);
        assertThat(transaction.getReceipts()).hasSize(1);
        verify(s3Service).saveFile(anyString(), same(receipt));
        verify(s3Service).generatePresignedUrl(anyString());
    }

    @Test
    void shouldRejectArchivedCategoryDuringCreate() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Account defaultAccount = EntityTestFactory.account(1L, user, BigDecimal.ZERO);
        Category archivedCategory = EntityTestFactory.category(4L, user, "Archived", TransactionType.EXPENSE, CategoryStatus.ARCHIVED);
        TransactionCreateRequestDTO dto = new TransactionCreateRequestDTO(
                BigDecimal.ONE,
                TransactionType.EXPENSE,
                4L,
                LocalDate.of(2026, 4, 1),
                "Expense",
                null
        );
        EntityTestFactory.linkDefaultAccount(user, defaultAccount);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(categoryService.findAccessibleById(dto.categoryId(), user)).thenReturn(archivedCategory);

        assertThatThrownBy(() -> transactionService.createTransaction(authentication, dto, List.of()))
                .isInstanceOf(CategoryIsNotActiveException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldRejectMismatchedTransactionAndCategoryTypes() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Account defaultAccount = EntityTestFactory.account(1L, user, BigDecimal.ZERO);
        Category category = EntityTestFactory.category(4L, user, "Salary", TransactionType.INCOME, CategoryStatus.ACTIVE);
        TransactionCreateRequestDTO dto = new TransactionCreateRequestDTO(
                BigDecimal.ONE,
                TransactionType.EXPENSE,
                4L,
                LocalDate.of(2026, 4, 1),
                "Expense",
                null
        );
        Transaction transaction = EntityTestFactory.transaction(null, user, null, null, dto.type(), dto.amount(), dto.date());
        EntityTestFactory.linkDefaultAccount(user, defaultAccount);

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(transactionMapper.toEntity(dto, user)).thenReturn(transaction);
        when(categoryService.findAccessibleById(dto.categoryId(), user)).thenReturn(category);

        assertThatThrownBy(() -> transactionService.createTransaction(authentication, dto, List.of()))
                .isInstanceOf(MoneyFlowTypeMismatchException.class);
    }

    @Test
    void shouldRecalculateBalancesWhenUpdatingTransactionAndChangingAccount() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Account sourceAccount = EntityTestFactory.account(1L, user, new BigDecimal("70.00"));
        Account targetAccount = EntityTestFactory.account(2L, user, new BigDecimal("20.00"));
        Category expenseCategory = EntityTestFactory.category(4L, user, "Groceries", TransactionType.EXPENSE, CategoryStatus.ACTIVE);
        Transaction existingTransaction = EntityTestFactory.transaction(
                9L,
                user,
                sourceAccount,
                expenseCategory,
                TransactionType.EXPENSE,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 4, 1)
        );
        TransactionCreateRequestDTO updateDto = new TransactionCreateRequestDTO(
                new BigDecimal("50.00"),
                TransactionType.EXPENSE,
                4L,
                LocalDate.of(2026, 4, 2),
                "Updated expense",
                2L
        );
        TransactionResponseDTO response = new TransactionResponseDTO(
                9L,
                2L,
                updateDto.amount(),
                null,
                updateDto.description(),
                updateDto.type(),
                List.of(),
                updateDto.date(),
                null,
                null
        );

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(transactionRepository.findActiveByIdAndUser(9L, user)).thenReturn(Optional.of(existingTransaction));
        when(accountRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(targetAccount));
        when(categoryService.findAccessibleById(4L, user)).thenReturn(expenseCategory);
        doAnswer(invocation -> {
            TransactionCreateRequestDTO dto = invocation.getArgument(0);
            Transaction transaction = invocation.getArgument(1);
            transaction.setAmount(dto.amount());
            transaction.setType(dto.type());
            transaction.setDescription(dto.description());
            transaction.setDate(dto.date());
            return null;
        }).when(transactionMapper).updateEntity(eq(updateDto), eq(existingTransaction));
        when(transactionRepository.save(existingTransaction)).thenReturn(existingTransaction);
        when(transactionMapper.toDto(existingTransaction, List.of())).thenReturn(response);

        TransactionResponseDTO result = transactionService.updateTransaction(9L, authentication, updateDto);

        assertThat(result).isEqualTo(response);
        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("100.00");
        assertThat(targetAccount.getBalance()).isEqualByComparingTo("-30.00");
        assertThat(existingTransaction.getAccount()).isEqualTo(targetAccount);
    }

    @Test
    void shouldRollbackBalanceAndDeleteReceiptsWhenDeletingTransaction() {
        User user = EntityTestFactory.user(1L, "user@example.com", true);
        Account account = EntityTestFactory.account(1L, user, new BigDecimal("30.00"));
        Category category = EntityTestFactory.category(4L, user, "Salary", TransactionType.INCOME, CategoryStatus.ACTIVE);
        Transaction transaction = EntityTestFactory.transaction(
                9L,
                user,
                account,
                category,
                TransactionType.INCOME,
                new BigDecimal("30.00"),
                LocalDate.of(2026, 4, 1)
        );
        UUID receiptId = UUID.randomUUID();
        EntityTestFactory.attachReceipts(transaction, new org.tc.mtracker.transaction.ReceiptImage(receiptId, transaction));

        when(userService.getCurrentAuthenticatedUser(authentication)).thenReturn(user);
        when(transactionRepository.findActiveByIdAndUser(9L, user)).thenReturn(Optional.of(transaction));

        transactionService.deleteTransaction(9L, authentication);

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        verify(s3Service).deleteFile("receipts/" + receiptId);
        verify(transactionRepository).delete(transaction);
    }
}
