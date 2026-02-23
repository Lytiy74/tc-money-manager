package org.tc.mtracker.category;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.tc.mtracker.category.dto.CategoryResponseDTO;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface CategoryMapper {

    CategoryResponseDTO toDto(Category category);

    List<CategoryResponseDTO> toListDto(List<Category> categories);
}
