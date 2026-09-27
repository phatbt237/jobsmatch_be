package vn.career.content.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MentorApplyRequest(
        @Size(max = 255) String company,
        @NotBlank @Size(max = 255) String jobTitle,
        @NotNull @Min(0) @Max(60) Integer yearsExperience,
        UUID majorId,
        @Size(max = 2000) String bio,
        @Size(max = 500) @Pattern(regexp = "^https://.+", message = "must start with https://") String linkedinUrl) {
}
