package vn.career.survey.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.career.survey.domain.QuestionType;

/** Full question as seen by an admin, including scoring metadata. */
public record AdminQuestionResponse(
        UUID id,
        UUID sectionId,
        int orderIndex,
        QuestionType type,
        String content,
        String dimensionCode,
        BigDecimal weight,
        boolean reverseScored,
        boolean attentionCheck,
        JsonNode expectedValue,
        boolean required,
        List<AdminOptionResponse> options) {
}
