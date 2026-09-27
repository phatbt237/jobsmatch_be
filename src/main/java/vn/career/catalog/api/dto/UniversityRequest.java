package vn.career.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.career.catalog.domain.Region;
import vn.career.catalog.domain.UniversityType;

/** {@code code} is only used on create. */
public record UniversityRequest(
        @Pattern(regexp = "^[A-Z0-9_]{2,50}$", message = "must be 2-50 upper case letters, digits or _") String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull Region region,
        @NotNull UniversityType type) {
}
