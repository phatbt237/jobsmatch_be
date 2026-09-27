package vn.career.survey.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

/** Everything an admin can set on a question. Used to create, update and copy questions. */
public record QuestionSpec(
        QuestionType type,
        String content,
        String dimensionCode,
        BigDecimal weight,
        boolean reverseScored,
        boolean attentionCheck,
        JsonNode expectedValue,
        boolean required,
        Integer orderIndex,
        List<OptionSpec> options) {

    public record OptionSpec(String label, BigDecimal value, boolean correct) {
    }
}
