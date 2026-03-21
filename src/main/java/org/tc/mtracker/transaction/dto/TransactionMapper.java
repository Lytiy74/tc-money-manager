package org.tc.mtracker.transaction.dto;

import org.mapstruct.*;
import org.tc.mtracker.category.CategoryMapper;
import org.tc.mtracker.category.CategoryService;
import org.tc.mtracker.transaction.Transaction;
import org.tc.mtracker.user.User;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {CategoryMapper.class, CategoryService.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TransactionMapper {

    @Mappings({
            @Mapping(target = Transaction.Fields.id, ignore = true),
            @Mapping(target = Transaction.Fields.user, source = "user"),
            @Mapping(target = Transaction.Fields.account, ignore = true),
            @Mapping(target = Transaction.Fields.category, ignore = true),
            @Mapping(target = Transaction.Fields.createdAt, ignore = true),
            @Mapping(target = Transaction.Fields.updatedAt, ignore = true),
            @Mapping(target = Transaction.Fields.deletedAt, ignore = true),
            @Mapping(target = Transaction.Fields.receipts, ignore = true)
    })
    Transaction toEntity(TransactionCreateRequestDTO dto, User user);

    TransactionResponseDTO toDto(Transaction transaction, List<String> receiptsUrls);
}
