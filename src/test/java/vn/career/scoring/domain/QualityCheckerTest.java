package vn.career.scoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityCheckerTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final QualityChecker checker = new QualityChecker(2, 3);

    private static ScoringItem likert(int answer) {
        return new ScoringItem("LIKERT", "R", 1, false, false, null, List.of(), JSON.numberNode(answer));
    }

    private static ScoringItem attention(int expected, Integer answer) {
        return new ScoringItem("LIKERT", null, 1, false, true, JSON.numberNode(expected), List.of(),
                answer == null ? null : JSON.numberNode(answer));
    }

    private static List<ScoringItem> variedLikerts(int count) {
        List<ScoringItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(likert(i % 5 + 1));
        }
        return items;
    }

    // ---------- attention checks ----------

    @Test
    void passingBothAttentionChecksIsFine() {
        List<ScoringItem> items = new ArrayList<>(variedLikerts(20));
        items.add(attention(4, 4));
        items.add(attention(1, 1));

        QualityFlags flags = checker.check(items, Duration.ofMinutes(20));

        assertThat(flags.attentionFailed()).isFalse();
        assertThat(flags.lowReliability()).isFalse();
    }

    @Test
    void failingOneOfTwoAttentionChecksIsNotEnough() {
        QualityFlags flags = checker.check(List.of(attention(4, 4), attention(1, 5)), Duration.ofMinutes(20));

        assertThat(flags.attentionFailed()).isFalse();
    }

    @Test
    void failingTwoAttentionChecksRaisesTheFlag() {
        QualityFlags flags = checker.check(List.of(attention(4, 2), attention(1, 5)), Duration.ofMinutes(20));

        assertThat(flags.attentionFailed()).isTrue();
        assertThat(flags.lowReliability()).isTrue();
    }

    @Test
    void anUnansweredAttentionCheckCountsAsFailed() {
        QualityFlags flags = checker.check(List.of(attention(4, null), attention(1, null)), Duration.ofMinutes(20));

        assertThat(flags.attentionFailed()).isTrue();
    }

    // ---------- straight lining ----------

    @Test
    void ninetyPercentTheSameAnswerIsStraightLining() {
        List<ScoringItem> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(likert(3));
        }
        items.add(likert(5));   // 9 of 10 = 90%

        QualityFlags flags = checker.check(items, Duration.ofMinutes(20));

        assertThat(flags.straightLining()).isTrue();
        assertThat(flags.lowReliability()).isTrue();
    }

    @Test
    void belowNinetyPercentIsNotStraightLining() {
        List<ScoringItem> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(likert(3));
        }
        items.add(likert(5));
        items.add(likert(1));   // 8 of 10 = 80%

        assertThat(checker.check(items, Duration.ofMinutes(20)).straightLining()).isFalse();
    }

    @Test
    void tooFewAnswersAreNotJudged() {
        assertThat(checker.check(List.of(likert(3), likert(3), likert(3)), Duration.ofMinutes(20)).straightLining())
                .isFalse();
    }

    @Test
    void attentionChecksAndNonLikertQuestionsDoNotCountTowardsStraightLining() {
        List<ScoringItem> items = new ArrayList<>(variedLikerts(10));
        for (int i = 0; i < 30; i++) {
            items.add(attention(3, 3));
        }

        assertThat(checker.check(items, Duration.ofMinutes(20)).straightLining()).isFalse();
    }

    // ---------- speed ----------

    @Test
    void finishingFasterThanThreeSecondsPerQuestionIsTooFast() {
        List<ScoringItem> items = variedLikerts(70);   // needs at least 210 s

        assertThat(checker.check(items, Duration.ofSeconds(209)).tooFast()).isTrue();
        assertThat(checker.check(items, Duration.ofSeconds(211)).tooFast()).isFalse();
    }

    @Test
    void tooFastAloneDoesNotMakeTheResultLowReliability() {
        QualityFlags flags = checker.check(variedLikerts(70), Duration.ofSeconds(5));

        assertThat(flags.tooFast()).isTrue();
        assertThat(flags.lowReliability()).isFalse();
    }

    @Test
    void thresholdFollowsConfiguration() {
        QualityChecker strict = new QualityChecker(2, 10);

        assertThat(strict.check(variedLikerts(10), Duration.ofSeconds(99)).tooFast()).isTrue();
        assertThat(strict.check(variedLikerts(10), Duration.ofSeconds(101)).tooFast()).isFalse();
    }

    // ---------- storage shape ----------

    @Test
    void toMapContainsEveryFlagAndTheDerivedOne() {
        QualityFlags flags = new QualityFlags(true, false, true);

        assertThat(flags.toMap()).containsEntry("ATTENTION_FAILED", true).containsEntry("STRAIGHT_LINING", false)
                .containsEntry("TOO_FAST", true).containsEntry("lowReliability", true);
    }
}
