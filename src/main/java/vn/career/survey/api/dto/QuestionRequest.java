package vn.career.survey.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.career.survey.domain.QuestionType;

/**
 * Create or update a question. {@code sectionId} is required on create and ignored on update (questions do not move).
 * Missing weight defaults to 1, missing required to true, missing orderIndex appends at the end of the section.
 * On update the options are replaced by the given list.
 */
public record QuestionRequest(
        UUID sectionId,
        @NotNull QuestionType type,
        @NotBlank @Size(max = 2000) String content,
        @Size(max = 30) String dimensionCode,
        @DecimalMin("0.01") @DecimalMax("100") BigDecimal weight,
        Boolean reverseScored,
        Boolean attentionCheck,
        JsonNode expectedValue,
        Boolean required,
        @Positive Integer orderIndex,
        @Size(max = 20) List<@Valid @NotNull OptionRequest> options) {
}
