package vn.career.auth.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import vn.career.auth.domain.Role;
import vn.career.auth.domain.UserStatus;

/** Public view of an account. Never contains the password hash. */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        LocalDate dateOfBirth,
        Role role,
        UserStatus status,
        Instant createdAt) {
}
