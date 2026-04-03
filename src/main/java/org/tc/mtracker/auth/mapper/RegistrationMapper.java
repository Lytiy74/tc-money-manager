package org.tc.mtracker.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.tc.mtracker.auth.dto.RegistrationResponseDto;
import org.tc.mtracker.user.User;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RegistrationMapper {
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "isActivated", source = "user.activated")
    RegistrationResponseDto toRegistrationResponseDto(User user, String avatarUrl);
}
