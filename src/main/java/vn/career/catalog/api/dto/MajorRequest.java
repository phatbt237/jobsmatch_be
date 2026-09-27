package vn.career.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code code} is only used on create (it never changes). Missing {@code active} means true. */
public record MajorRequest(
        @Pattern(regexp = "^[A-Z0-9_]{2,50}$", message = "must be 2-50 upper case letters, digits or _") String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String groupName,
        @Size(max = 500) String shortDescription,
        @Size(max = 20000) String description,
        @Size(max = 15) List<@Pattern(regexp = "^[A-Z]\\d{2}$", message = "must look like A00, D01") String> combos,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> typicalJobs,
        Boolean active) {
}
