package vn.career.survey.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Checks that a raw answer has the right shape for its question type. Pure logic, no Spring. */
public final class AnswerValidator {

    private AnswerValidator() {
    }

    /** Returns an error message, or empty if the answer is acceptable. */
    public static Optional<String> validate(Question question, JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return Optional.of("value is required");
        }
        return switch (question.getType()) {
            case LIKERT -> value.isIntegralNumber() && value.canConvertToInt()
                    && value.intValue() >= 1 && value.intValue() <= 5
                    ? Optional.empty()
                    : Optional.of("must be an integer from 1 to 5");
            case SINGLE_CHOICE, MINI_TEST -> value.isTextual() && optionIds(question).contains(normalize(value.asText()))
                    ? Optional.empty()
                    : Optional.of("must be the id of one of the question options");
            case RANKING -> validateRanking(question, value);
            case NUMERIC_INPUT -> value.isNumber() ? Optional.empty() : Optional.of("must be a number");
        };
    }

    private static Optional<String> validateRanking(Question question, JsonNode value) {
        Set<String> expected = optionIds(question);
        if (!value.isArray() || value.size() != expected.size()) {
            return Optional.of("must list every option id exactly once, best first");
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || !expected.contains(normalize(item.asText())) || !seen.add(normalize(item.asText()))) {
                return Optional.of("must list every option id exactly once, best first");
            }
        }
        return Optional.empty();
    }

    private static Set<String> optionIds(Question question) {
        return question.getOptions().stream()
                .map(option -> normalize(option.getId().toString()))
                .collect(Collectors.toSet());
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
