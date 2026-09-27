package vn.career.survey.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One answer. LIKERT: integer 1..5. SINGLE_CHOICE / MINI_TEST: option id.
 * RANKING: array of option ids. NUMERIC_INPUT: number.
 */
public record AnswerItem(@NotNull UUID questionId, @NotNull JsonNode value) {
}
