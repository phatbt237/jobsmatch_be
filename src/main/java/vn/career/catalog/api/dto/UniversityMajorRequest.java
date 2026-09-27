package vn.career.catalog.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** {@code universityId} and {@code majorId} are only used on create. Scores are out of 30. */
public record UniversityMajorRequest(
        UUID universityId,
        UUID majorId,
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotBlank @Pattern(regexp = "^[A-Z]\\d{2}$", message = "must look like A00, D01") String combo,
        @DecimalMin("0") @DecimalMax("30") BigDecimal cutoffScore,
        @PositiveOrZero Long tuitionPerYear) {
}
