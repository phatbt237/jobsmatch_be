package vn.career.auth.infrastructure;

import org.mapstruct.Mapper;
import vn.career.auth.api.dto.UserResponse;
import vn.career.auth.domain.User;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
