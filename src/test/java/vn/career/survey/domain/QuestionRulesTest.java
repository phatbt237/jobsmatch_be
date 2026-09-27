package vn.career.survey.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.career.survey.domain.SurveyTestData.option;
import static vn.career.survey.domain.SurveyTestData.plainOptions;
import static vn.career.survey.domain.SurveyTestData.spec;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import vn.career.common.api.ErrorDetail;

class QuestionRulesTest {

    private static final Predicate<String> KNOWN = Set.of("R", "LOGIC", "VAL_INCOME")::contains;

    private static List<String> fieldsOf(List<ErrorDetail> problems) {
        return problems.stream().map(ErrorDetail::field).toList();
    }

    private static QuestionSpec with(QuestionSpec base, BigDecimal weight, boolean reverse, boolean attention,
                                     com.fasterxml.jackson.databind.JsonNode expected) {
        return new QuestionSpec(base.type(), base.content(), base.dimensionCode(), weight, reverse, attention,
                expected, base.required(), base.orderIndex(), base.options());
    }

    @Test
    void validLikertPasses() {
        assertThat(QuestionRules.validate(spec(QuestionType.LIKERT, "R"), KNOWN)).isEmpty();
    }

    @Test
    void likertNeedsDimensionAndNoOptions() {
        assertThat(fieldsOf(QuestionRules.validate(spec(QuestionType.LIKERT, null), KNOWN))).contains("dimensionCode");
        assertThat(fieldsOf(QuestionRules.validate(spec(QuestionType.LIKERT, "R", plainOptions(2)), KNOWN)))
                .contains("options");
    }

    @Test
    void unknownDimensionIsRejected() {
        assertThat(fieldsOf(QuestionRules.validate(spec(QuestionType.LIKERT, "ZZZ"), KNOWN))).contains("dimensionCode");
    }

    @Test
    void weightMustBePositive() {
        QuestionSpec zero = with(spec(QuestionType.LIKERT, "R"), BigDecimal.ZERO, false, false, null);
        QuestionSpec missing = with(spec(QuestionType.LIKERT, "R"), null, false, false, null);

        assertThat(fieldsOf(QuestionRules.validate(zero, KNOWN))).contains("weight");
        assertThat(fieldsOf(QuestionRules.validate(missing, KNOWN))).contains("weight");
    }

    @Test
    void miniTestNeedsExactlyOneCorrectOption() {
        QuestionSpec none = spec(QuestionType.MINI_TEST, "LOGIC", plainOptions(3));
        QuestionSpec two = spec(QuestionType.MINI_TEST, "LOGIC", List.of(
                option("a", null, true), option("b", null, true), option("c", null, false)));
        QuestionSpec one = spec(QuestionType.MINI_TEST, "LOGIC", List.of(
                option("a", null, true), option("b", null, false)));

        assertThat(fieldsOf(QuestionRules.validate(none, KNOWN))).contains("options");
        assertThat(fieldsOf(QuestionRules.validate(two, KNOWN))).contains("options");
        assertThat(QuestionRules.validate(one, KNOWN)).isEmpty();
    }

    @Test
    void choiceQuestionsNeedAtLeastTwoOptions() {
        assertThat(fieldsOf(QuestionRules.validate(
                spec(QuestionType.SINGLE_CHOICE, "R", plainOptions(1)), KNOWN))).contains("options");
        assertThat(fieldsOf(QuestionRules.validate(
                spec(QuestionType.RANKING, "R", plainOptions(1)), KNOWN))).contains("options");
    }

    @Test
    void singleChoiceOptionsNeedScores() {
        QuestionSpec noScores = spec(QuestionType.SINGLE_CHOICE, "R", List.of(
                option("a", null, false), option("b", BigDecimal.ONE, false)));

        assertThat(fieldsOf(QuestionRules.validate(noScores, KNOWN))).contains("options");
        assertThat(QuestionRules.validate(spec(QuestionType.SINGLE_CHOICE, "R", plainOptions(3)), KNOWN)).isEmpty();
    }

    @Test
    void onlyMiniTestsMayMarkACorrectOption() {
        QuestionSpec ranking = spec(QuestionType.RANKING, "R", List.of(
                option("a", null, true), option("b", null, false)));

        assertThat(fieldsOf(QuestionRules.validate(ranking, KNOWN))).contains("options");
    }

    @Test
    void numericInputTakesNoOptions() {
        assertThat(QuestionRules.validate(spec(QuestionType.NUMERIC_INPUT, null), KNOWN)).isEmpty();
        assertThat(fieldsOf(QuestionRules.validate(
                spec(QuestionType.NUMERIC_INPUT, null, plainOptions(2)), KNOWN))).contains("options");
    }

    // ---------- attention checks ----------

    @Test
    void validAttentionCheckPasses() {
        QuestionSpec check = with(spec(QuestionType.LIKERT, null), BigDecimal.ONE, false, true, SurveyTestData.number(4));

        assertThat(QuestionRules.validate(check, KNOWN)).isEmpty();
    }

    @Test
    void attentionCheckNeedsAnExpectedLikertValue() {
        QuestionSpec missing = with(spec(QuestionType.LIKERT, null), BigDecimal.ONE, false, true, null);
        QuestionSpec outOfRange = with(spec(QuestionType.LIKERT, null), BigDecimal.ONE, false, true, SurveyTestData.number(9));

        assertThat(fieldsOf(QuestionRules.validate(missing, KNOWN))).contains("expectedValue");
        assertThat(fieldsOf(QuestionRules.validate(outOfRange, KNOWN))).contains("expectedValue");
    }

    @Test
    void attentionCheckCannotMeasureADimensionOrBeReversed() {
        QuestionSpec check = with(spec(QuestionType.LIKERT, "R"), BigDecimal.ONE, true, true, SurveyTestData.number(4));

        assertThat(fieldsOf(QuestionRules.validate(check, KNOWN))).contains("dimensionCode", "reverseScored");
    }

    @Test
    void attentionCheckMustBeLikert() {
        QuestionSpec check = with(spec(QuestionType.NUMERIC_INPUT, null), BigDecimal.ONE, false, true, SurveyTestData.number(4));

        assertThat(fieldsOf(QuestionRules.validate(check, KNOWN))).contains("type");
    }

    @Test
    void expectedValueOnRegularQuestionIsRejected() {
        QuestionSpec regular = with(spec(QuestionType.LIKERT, "R"), BigDecimal.ONE, false, false, SurveyTestData.number(4));

        assertThat(fieldsOf(QuestionRules.validate(regular, KNOWN))).contains("expectedValue");
    }
}
