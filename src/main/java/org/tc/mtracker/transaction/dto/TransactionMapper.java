package org.tc.mtracker.transaction.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.tc.mtracker.category.CategoryMapper;
import org.tc.mtracker.category.CategoryService;
import org.tc.mtracker.transaction.Transaction;
import org.tc.mtracker.user.User;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {CategoryMapper.class, CategoryService.class},
        unmappedTargetPolicy = ReportingPolicy.WARN)
public interface TransactionMapper {

    @Mapping(target = Transaction.Fields.user, source = "user")
    Transaction toEntity(TransactionCreateRequestDTO dto, User user);

    TransactionResponseDTO toDto(Transaction transaction, List<String> receiptsUrls);
}
