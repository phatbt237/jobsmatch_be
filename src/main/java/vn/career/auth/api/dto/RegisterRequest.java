package vn.career.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import vn.career.auth.domain.Role;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // 72 is the BCrypt input limit
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password,
        @NotBlank @Size(max = 255) String fullName,
        @Past LocalDate dateOfBirth,
        @NotNull Role role) {
}
