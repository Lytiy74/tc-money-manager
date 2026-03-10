    package org.tc.mtracker.user.dto;

    import org.mapstruct.*;
    import org.tc.mtracker.user.User;

    @Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
    public abstract class UserMapper {
        public abstract void updateEntityFromDto(RequestUpdateUserProfileDTO dto, @MappingTarget User user);

        @Mapping(target = "avatarUrl", source = "avatarUrl")
        @Mapping(target = "isActivated", source = "user.activated")
        public abstract ResponseUserDTO toDto(User user, String avatarUrl);
    }
