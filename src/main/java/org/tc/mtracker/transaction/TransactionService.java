package org.tc.mtracker.transaction;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tc.mtracker.category.Category;
import org.tc.mtracker.category.CategoryService;
import org.tc.mtracker.transaction.dto.TransactionCreateRequestDTO;
import org.tc.mtracker.transaction.dto.TransactionMapper;
import org.tc.mtracker.transaction.dto.TransactionResponseDTO;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.S3Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final TransactionMapper transactionMapper;
    private final UserService userService;
    private final S3Service s3Service;

    @Transactional
    public TransactionResponseDTO saveTransaction(Authentication auth, TransactionCreateRequestDTO createRequestDTO) {
        User user = userService.getCurrentAuthenticatedUser(auth);

        Transaction transaction = transactionMapper.toEntity(createRequestDTO, user);
        Category category = categoryService.findById(createRequestDTO.categoryId());

        transaction.setUser(user);
        transaction.setCategory(category);

        Transaction saved = transactionRepository.save(transaction);

        List<String> presignedUrls = generatePresignedUrlsForReceipts(saved);

        return transactionMapper.toDto(saved, presignedUrls);
    }

    private List<String> generatePresignedUrlsForReceipts(Transaction saved) {
        return saved.getReceipts().stream()
                .map(i -> s3Service.generatePresignedUrl(i.getId().toString())).toList();
    }

}
