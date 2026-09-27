package vn.career.survey.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import vn.career.common.api.ErrorDetail;

/** Consistency rules for a question definition, checked whenever an admin saves a question. Pure logic. */
public final class QuestionRules {

    private QuestionRules() {
    }

    /** Returns the list of problems, empty if the spec is valid. */
    public static List<ErrorDetail> validate(QuestionSpec spec, Predicate<String> dimensionExists) {
        List<ErrorDetail> problems = new ArrayList<>();
        int optionCount = spec.options() == null ? 0 : spec.options().size();
        long correctCount = spec.options() == null ? 0 : spec.options().stream().filter(QuestionSpec.OptionSpec::correct).count();

        if (spec.weight() == null || spec.weight().compareTo(BigDecimal.ZERO) <= 0) {
            problems.add(new ErrorDetail("weight", "must be greater than 0"));
        }

        if (spec.attentionCheck()) {
            if (spec.type() != QuestionType.LIKERT) {
                problems.add(new ErrorDetail("type", "an attention check must be a LIKERT question"));
            }
            if (spec.dimensionCode() != null) {
                problems.add(new ErrorDetail("dimensionCode", "an attention check must not measure a dimension"));
            }
            if (spec.reverseScored()) {
                problems.add(new ErrorDetail("reverseScored", "an attention check cannot be reverse scored"));
            }
            if (!isLikertValue(spec.expectedValue())) {
                problems.add(new ErrorDetail("expectedValue", "an attention check needs an expected integer from 1 to 5"));
            }
        } else {
            if (spec.expectedValue() != null && !spec.expectedValue().isNull()) {
                problems.add(new ErrorDetail("expectedValue", "only attention checks can have an expected value"));
            }
            switch (spec.type()) {
                case LIKERT -> {
                    requireDimension(spec, problems);
                    requireNoOptions(optionCount, problems);
                }
                case RANKING -> {
                    requireDimension(spec, problems);
                    requireAtLeastTwoOptions(optionCount, problems);
                    requireNoCorrect(correctCount, problems);
                }
                case SINGLE_CHOICE -> {
                    requireDimension(spec, problems);
                    requireAtLeastTwoOptions(optionCount, problems);
                    requireNoCorrect(correctCount, problems);
                    if (spec.options() != null && spec.options().stream().anyMatch(o -> o.value() == null)) {
                        problems.add(new ErrorDetail("options", "every option of a SINGLE_CHOICE question needs a value"));
                    }
                }
                case MINI_TEST -> {
                    requireDimension(spec, problems);
                    requireAtLeastTwoOptions(optionCount, problems);
                    if (correctCount != 1) {
                        problems.add(new ErrorDetail("options", "a MINI_TEST needs exactly one correct option"));
                    }
                }
                case NUMERIC_INPUT -> requireNoOptions(optionCount, problems);
            }
        }

        if (spec.dimensionCode() != null && !dimensionExists.test(spec.dimensionCode())) {
            problems.add(new ErrorDetail("dimensionCode", "unknown dimension"));
        }
        return problems;
    }

    private static boolean isLikertValue(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToInt()
                && value.intValue() >= 1 && value.intValue() <= 5;
    }

    private static void requireDimension(QuestionSpec spec, List<ErrorDetail> problems) {
        if (spec.dimensionCode() == null || spec.dimensionCode().isBlank()) {
            problems.add(new ErrorDetail("dimensionCode", "is required for " + spec.type() + " questions"));
        }
    }

    private static void requireNoOptions(int optionCount, List<ErrorDetail> problems) {
        if (optionCount > 0) {
            problems.add(new ErrorDetail("options", "this question type does not take options"));
        }
    }

    private static void requireAtLeastTwoOptions(int optionCount, List<ErrorDetail> problems) {
        if (optionCount < 2) {
            problems.add(new ErrorDetail("options", "at least 2 options are required"));
        }
    }

    private static void requireNoCorrect(long correctCount, List<ErrorDetail> problems) {
        if (correctCount > 0) {
            problems.add(new ErrorDetail("options", "only MINI_TEST options can be marked correct"));
        }
    }
}
