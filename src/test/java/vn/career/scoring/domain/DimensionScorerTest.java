package vn.career.scoring.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DimensionScorerTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final DimensionScorer scorer = new DimensionScorer();

    private static ScoringItem likert(String dimension, int answer, boolean reverse) {
        return new ScoringItem("LIKERT", dimension, 1, reverse, false, null, List.of(), JSON.numberNode(answer));
    }

    private static List<ScoringItem> likerts(String dimension, int count, int answer) {
        List<ScoringItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(likert(dimension, answer, false));
        }
        return items;
    }

    // ---------- reverse scoring ----------

    @Test
    void reverseScoredAnswerIsMirrored() {
        // answering 5 to a reversed statement is worth 1 point
        DimensionScore score = scorer.score(List.of(likert("R", 5, true))).get("R");

        assertThat(score.raw()).isEqualTo(1);
        assertThat(score.normalized()).isEqualTo(0);
    }

    @Test
    void reverseScoredOneBecomesFive() {
        DimensionScore score = scorer.score(List.of(likert("R", 1, true))).get("R");

        assertThat(score.raw()).isEqualTo(5);
        assertThat(score.normalized()).isEqualTo(1);
    }

    @Test
    void reverseAndNormalStatementsCancelOutWhenAnsweredConsistently() {
        // 5 on a normal statement and 1 on the reversed one both mean "very high"
        DimensionScore score = scorer.score(List.of(likert("I", 5, false), likert("I", 1, true))).get("I");

        assertThat(score.raw()).isEqualTo(10);
        assertThat(score.normalized()).isEqualTo(1);
    }

    // ---------- normalisation ----------

    @Test
    void allLowestAnswersGiveZero() {
        DimensionScore score = scorer.score(likerts("A", 6, 1)).get("A");

        assertThat(score.normalized()).isEqualTo(0);
        assertThat(score.raw()).isEqualTo(6);
        assertThat(score.min()).isEqualTo(6);
        assertThat(score.max()).isEqualTo(30);
    }

    @Test
    void allHighestAnswersGiveOne() {
        assertThat(scorer.score(likerts("A", 6, 5)).get("A").normalized()).isEqualTo(1);
    }

    @Test
    void middleAnswersGiveHalf() {
        assertThat(scorer.score(likerts("A", 6, 3)).get("A").normalized()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void normalizedIsAlwaysWithinZeroAndOne() {
        for (int answer = 1; answer <= 5; answer++) {
            for (boolean reverse : new boolean[] {false, true}) {
                double normalized = scorer.score(List.of(likert("S", answer, reverse))).get("S").normalized();
                assertThat(normalized).isBetween(0.0, 1.0);
            }
        }
    }

    @Test
    void weightsScaleTheContributionAndTheRange() {
        ScoringItem heavy = new ScoringItem("LIKERT", "E", 3, false, false, null, List.of(), JSON.numberNode(5));
        ScoringItem light = new ScoringItem("LIKERT", "E", 1, false, false, null, List.of(), JSON.numberNode(1));

        DimensionScore score = scorer.score(List.of(heavy, light)).get("E");

        assertThat(score.raw()).isEqualTo(3 * 5 + 1);
        assertThat(score.min()).isEqualTo(3 + 1);
        assertThat(score.max()).isEqualTo(15 + 5);
        assertThat(score.normalized()).isCloseTo((16.0 - 4) / (20 - 4), within(1e-9));
    }

    @Test
    void dimensionsAreScoredIndependentlyAndSortedByCode() {
        List<ScoringItem> items = new ArrayList<>(likerts("R", 2, 5));
        items.addAll(likerts("A", 2, 1));

        Map<String, DimensionScore> scores = scorer.score(items);

        assertThat(scores.keySet()).containsExactly("A", "R");
        assertThat(scores.get("R").normalized()).isEqualTo(1);
        assertThat(scores.get("A").normalized()).isEqualTo(0);
    }

    // ---------- mini tests ----------

    private static ScoringItem miniTest(String chosenId) {
        List<OptionInput> options = List.of(
                new OptionInput("a", 1, null, false), new OptionInput("b", 2, null, true), new OptionInput("c", 3, null, false));
        return new ScoringItem("MINI_TEST", "LOGIC", 4, false, false, null, options, JSON.textNode(chosenId));
    }

    @Test
    void correctMiniTestAnswerScoresOneTimesWeight() {
        DimensionScore score = scorer.score(List.of(miniTest("b"))).get("LOGIC");

        assertThat(score.raw()).isEqualTo(4);
        assertThat(score.min()).isEqualTo(0);
        assertThat(score.max()).isEqualTo(4);
        assertThat(score.normalized()).isEqualTo(1);
    }

    @Test
    void wrongMiniTestAnswerScoresZero() {
        DimensionScore score = scorer.score(List.of(miniTest("a"))).get("LOGIC");

        assertThat(score.raw()).isEqualTo(0);
        assertThat(score.normalized()).isEqualTo(0);
    }

    @Test
    void miniTestAndLikertMixInOneDimension() {
        DimensionScore score = scorer.score(List.of(likert("LOGIC", 5, false), miniTest("b"))).get("LOGIC");

        assertThat(score.normalized()).isEqualTo(1);
    }

    // ---------- other question types ----------

    @Test
    void singleChoiceUsesTheOptionScore() {
        List<OptionInput> options = List.of(new OptionInput("x", 1, 1.0, false), new OptionInput("y", 2, 3.0, false),
                new OptionInput("z", 3, 5.0, false));
        ScoringItem item = new ScoringItem("SINGLE_CHOICE", "VAL_INCOME", 1, false, false, null, options, JSON.textNode("y"));

        DimensionScore score = scorer.score(List.of(item)).get("VAL_INCOME");

        assertThat(score.raw()).isEqualTo(3);
        assertThat(score.normalized()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void rankingMeasuresWhereTheFirstListedOptionWasPlaced() {
        List<OptionInput> options = List.of(new OptionInput("first", 1, null, false),
                new OptionInput("second", 2, null, false), new OptionInput("third", 3, null, false));
        JsonNode top = JSON.arrayNode().add("first").add("second").add("third");
        JsonNode middle = JSON.arrayNode().add("second").add("first").add("third");
        JsonNode bottom = JSON.arrayNode().add("second").add("third").add("first");

        double atTop = scorer.score(List.of(ranking(options, top))).get("VAL_TEAMWORK").normalized();
        double atMiddle = scorer.score(List.of(ranking(options, middle))).get("VAL_TEAMWORK").normalized();
        double atBottom = scorer.score(List.of(ranking(options, bottom))).get("VAL_TEAMWORK").normalized();

        assertThat(atTop).isEqualTo(1);
        assertThat(atMiddle).isCloseTo(0.5, within(1e-9));
        assertThat(atBottom).isEqualTo(0);
    }

    private static ScoringItem ranking(List<OptionInput> options, JsonNode answer) {
        return new ScoringItem("RANKING", "VAL_TEAMWORK", 1, false, false, null, options, answer);
    }

    // ---------- things that must be ignored ----------

    @Test
    void attentionChecksQuestionsWithoutDimensionAndUnansweredQuestionsAreIgnored() {
        ScoringItem attention = new ScoringItem("LIKERT", null, 1, false, true, JSON.numberNode(4), List.of(),
                JSON.numberNode(4));
        ScoringItem unanswered = new ScoringItem("LIKERT", "R", 1, false, false, null, List.of(), null);
        ScoringItem nullAnswer = new ScoringItem("LIKERT", "R", 1, false, false, null, List.of(), JSON.nullNode());
        ScoringItem numeric = new ScoringItem("NUMERIC_INPUT", "R", 1, false, false, null, List.of(), JSON.numberNode(42));

        assertThat(scorer.score(List.of(attention, unanswered, nullAnswer, numeric))).isEmpty();
    }

    @Test
    void answersOfTheWrongShapeAreTreatedAsUnanswered() {
        ScoringItem badLikert = new ScoringItem("LIKERT", "R", 1, false, false, null, List.of(), JSON.numberNode(9));
        ScoringItem textLikert = new ScoringItem("LIKERT", "R", 1, false, false, null, List.of(), JSON.textNode("5"));
        ScoringItem unknownOption = miniTest("does-not-exist");

        Map<String, DimensionScore> scores = scorer.score(List.of(badLikert, textLikert, unknownOption));

        assertThat(scores).isEmpty();
    }

    @Test
    void emptyInputGivesEmptyResult() {
        assertThat(scorer.score(List.of())).isEmpty();
    }
}
