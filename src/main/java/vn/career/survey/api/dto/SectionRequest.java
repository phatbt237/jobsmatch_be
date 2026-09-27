package vn.career.survey.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** {@code orderIndex} is optional: when missing the section is appended at the end. */
public record SectionRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9_]{1,10}$", message = "must be 1-10 upper case letters, digits or _")
        String code,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @Positive Integer orderIndex) {
}
