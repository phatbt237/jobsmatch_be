package vn.career.scoring.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * One survey question together with the student's answer. {@code type} is the question type name
 * (LIKERT, MINI_TEST, SINGLE_CHOICE, RANKING, NUMERIC_INPUT); {@code answer} is null when unanswered.
 */
public record ScoringItem(
        String type,
        String dimension,
        double weight,
        boolean reverseScored,
        boolean attentionCheck,
        JsonNode expectedValue,
        List<OptionInput> options,
        JsonNode answer) {
}
