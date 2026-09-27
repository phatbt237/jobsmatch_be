package vn.career.scoring.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-quality checks. Pure Java.
 * <ul>
 *   <li>ATTENTION_FAILED: at least {@code attentionFailThreshold} attention checks answered wrongly (or not at all)</li>
 *   <li>STRAIGHT_LINING: at least 90% of the Likert answers share one value (only judged from 10 answers up)</li>
 *   <li>TOO_FAST: total time below {@code minSecondsPerQuestion} x number of questions</li>
 * </ul>
 */
public final class QualityChecker {

    static final double STRAIGHT_LINING_SHARE = 0.9;
    static final int STRAIGHT_LINING_MIN_ANSWERS = 10;

    private final int attentionFailThreshold;
    private final double minSecondsPerQuestion;

    public QualityChecker(int attentionFailThreshold, double minSecondsPerQuestion) {
        this.attentionFailThreshold = attentionFailThreshold;
        this.minSecondsPerQuestion = minSecondsPerQuestion;
    }

    public QualityFlags check(List<ScoringItem> items, Duration elapsed) {
        return new QualityFlags(attentionFailed(items), straightLining(items), tooFast(items, elapsed));
    }

    private boolean attentionFailed(List<ScoringItem> items) {
        long failed = items.stream()
                .filter(ScoringItem::attentionCheck)
                .filter(item -> !sameInteger(item.answer(), item.expectedValue()))
                .count();
        return failed >= attentionFailThreshold;
    }

    private static boolean sameInteger(JsonNode answer, JsonNode expected) {
        return answer != null && expected != null && answer.isIntegralNumber() && expected.isIntegralNumber()
                && answer.canConvertToInt() && expected.canConvertToInt() && answer.intValue() == expected.intValue();
    }

    private boolean straightLining(List<ScoringItem> items) {
        Map<Integer, Integer> counts = new HashMap<>();
        int total = 0;
        for (ScoringItem item : items) {
            JsonNode answer = item.answer();
            if ("LIKERT".equals(item.type()) && !item.attentionCheck() && answer != null
                    && answer.isIntegralNumber() && answer.canConvertToInt()) {
                counts.merge(answer.intValue(), 1, Integer::sum);
                total++;
            }
        }
        if (total < STRAIGHT_LINING_MIN_ANSWERS) {
            return false;
        }
        int most = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return most >= STRAIGHT_LINING_SHARE * total;
    }

    private boolean tooFast(List<ScoringItem> items, Duration elapsed) {
        if (elapsed == null || elapsed.isNegative() || items.isEmpty()) {
            return false;
        }
        return elapsed.toMillis() / 1000.0 < minSecondsPerQuestion * items.size();
    }
}
