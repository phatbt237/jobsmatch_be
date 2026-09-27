package vn.career.survey.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Option order follows the position in the list. */
public record OptionRequest(@NotBlank @Size(max = 500) String label, BigDecimal value, Boolean correct) {
}
