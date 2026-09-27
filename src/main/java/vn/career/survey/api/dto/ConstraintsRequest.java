package vn.career.survey.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Section D. All fields are optional; the request replaces whatever was saved before. */
public record ConstraintsRequest(
        @Size(max = 20) Map<@Pattern(regexp = "^[a-z_]{1,30}$", message = "subject code must be lowercase letters or _")
                String, @DecimalMin("0") @DecimalMax("10") BigDecimal> gpa,
        @Size(max = 10) List<@Pattern(regexp = "^[A-Z]\\d{2}$", message = "must look like A00, D01") String> combos,
        @Size(max = 10) List<@Pattern(regexp = "^[A-Z_]{2,20}$", message = "must be upper case letters or _")
                String> preferredRegions,
        @PositiveOrZero Long budgetPerYear,
        @Min(1) @Max(5) Integer familyPressure) {
}
