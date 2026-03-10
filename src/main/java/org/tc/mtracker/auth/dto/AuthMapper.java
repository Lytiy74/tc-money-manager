package org.tc.mtracker.auth.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.tc.mtracker.user.User;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "isActivated", source = "user.activated")
    AuthResponseDTO toAuthResponseDTO(User user, String avatarUrl);
}
