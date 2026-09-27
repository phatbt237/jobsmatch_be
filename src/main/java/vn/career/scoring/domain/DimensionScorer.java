package vn.career.scoring.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Computes a score per dimension from the answers. Pure Java, no framework.
 *
 * <pre>
 * value        LIKERT 1..5 (6 - v when reverse scored) | MINI_TEST 1 or 0 | RANKING and SINGLE_CHOICE see below
 * contribution = value x weight
 * raw          = sum of contributions
 * min / max    = sum of (lowest / highest possible value x weight) over the answered questions
 * normalized   = (raw - min) / (max - min), always within [0, 1]
 * </pre>
 *
 * Questions without a dimension (attention checks, information only) and unanswered questions are ignored, and an
 * answer of the wrong shape counts as unanswered instead of failing the whole run.
 * <ul>
 *   <li>SINGLE_CHOICE: the value is the score of the chosen option, the range is the lowest to highest option score.</li>
 *   <li>RANKING: the question measures its dimension by where the student ranks the FIRST listed option (the item
 *   that stands for the dimension). Rank 1 gives 5 points, the last rank gives 1, the ranks between are evenly spread.</li>
 * </ul>
 */
public final class DimensionScorer {

    private static final double LIKERT_MIN = 1;
    private static final double LIKERT_MAX = 5;

    private record Accumulator(double raw, double min, double max) {
        Accumulator add(double value, double lowest, double highest, double weight) {
            return new Accumulator(raw + value * weight, min + lowest * weight, max + highest * weight);
        }
    }

    /** Result is sorted by dimension code. */
    public Map<String, DimensionScore> score(List<ScoringItem> items) {
        Map<String, Accumulator> totals = new HashMap<>();
        for (ScoringItem item : items) {
            if (item.dimension() == null || item.attentionCheck() || item.answer() == null || item.answer().isNull()) {
                continue;
            }
            Contribution contribution = contributionOf(item);
            if (contribution == null) {
                continue;
            }
            totals.merge(item.dimension(),
                    new Accumulator(0, 0, 0).add(contribution.value, contribution.lowest, contribution.highest, item.weight()),
                    (a, b) -> new Accumulator(a.raw + b.raw, a.min + b.min, a.max + b.max));
        }

        Map<String, DimensionScore> result = new TreeMap<>();
        totals.forEach((dimension, total) -> result.put(dimension, new DimensionScore(
                dimension, total.raw, total.min, total.max, normalize(total.raw, total.min, total.max))));
        return new LinkedHashMap<>(result);
    }

    private static double normalize(double raw, double min, double max) {
        if (max <= min) {
            return 0;
        }
        return Math.min(1, Math.max(0, (raw - min) / (max - min)));
    }

    private record Contribution(double value, double lowest, double highest) {
    }

    private static Contribution contributionOf(ScoringItem item) {
        JsonNode answer = item.answer();
        return switch (item.type()) {
            case "LIKERT" -> likert(item, answer);
            case "MINI_TEST" -> miniTest(item, answer);
            case "SINGLE_CHOICE" -> singleChoice(item, answer);
            case "RANKING" -> ranking(item, answer);
            default -> null;   // NUMERIC_INPUT and unknown types are not scored
        };
    }

    private static Contribution likert(ScoringItem item, JsonNode answer) {
        if (!answer.isIntegralNumber() || !answer.canConvertToInt()
                || answer.intValue() < LIKERT_MIN || answer.intValue() > LIKERT_MAX) {
            return null;
        }
        double value = item.reverseScored() ? 6 - answer.intValue() : answer.intValue();
        return new Contribution(value, LIKERT_MIN, LIKERT_MAX);
    }

    private static Contribution miniTest(ScoringItem item, JsonNode answer) {
        if (!answer.isTextual()) {
            return null;
        }
        return item.options().stream()
                .filter(o -> o.id().equalsIgnoreCase(answer.asText().trim()))
                .findFirst()
                .map(chosen -> new Contribution(chosen.correct() ? 1 : 0, 0, 1))
                .orElse(null);
    }

    private static Contribution singleChoice(ScoringItem item, JsonNode answer) {
        if (!answer.isTextual()) {
            return null;
        }
        OptionInput chosen = item.options().stream()
                .filter(o -> o.id().equalsIgnoreCase(answer.asText().trim()))
                .findFirst().orElse(null);
        if (chosen == null || chosen.value() == null) {
            return null;
        }
        OptionalDouble lowest = item.options().stream().filter(o -> o.value() != null).mapToDouble(OptionInput::value).min();
        OptionalDouble highest = item.options().stream().filter(o -> o.value() != null).mapToDouble(OptionInput::value).max();
        return new Contribution(chosen.value(), lowest.orElse(chosen.value()), highest.orElse(chosen.value()));
    }

    private static Contribution ranking(ScoringItem item, JsonNode answer) {
        if (!answer.isArray() || answer.size() < 2 || item.options().isEmpty()) {
            return null;
        }
        OptionInput anchor = item.options().stream().min(Comparator.comparingInt(OptionInput::orderIndex)).orElseThrow();
        int position = -1;
        for (int i = 0; i < answer.size(); i++) {
            if (answer.get(i).isTextual() && answer.get(i).asText().trim().equalsIgnoreCase(anchor.id())) {
                position = i;
                break;
            }
        }
        if (position < 0) {
            return null;
        }
        double value = LIKERT_MAX - (LIKERT_MAX - LIKERT_MIN) * position / (answer.size() - 1);
        return new Contribution(value, LIKERT_MIN, LIKERT_MAX);
    }
}
