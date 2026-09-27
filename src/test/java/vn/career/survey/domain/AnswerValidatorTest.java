package vn.career.survey.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.career.survey.domain.SurveyTestData.array;
import static vn.career.survey.domain.SurveyTestData.number;
import static vn.career.survey.domain.SurveyTestData.plainOptions;
import static vn.career.survey.domain.SurveyTestData.question;
import static vn.career.survey.domain.SurveyTestData.spec;
import static vn.career.survey.domain.SurveyTestData.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerValidatorTest {

    // ---------- LIKERT ----------

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void likertAcceptsOneToFive(int value) {
        Question q = question(spec(QuestionType.LIKERT, "R"));

        assertThat(AnswerValidator.validate(q, number(value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1, 100})
    void likertRejectsOutOfRange(int value) {
        Question q = question(spec(QuestionType.LIKERT, "R"));

        assertThat(AnswerValidator.validate(q, number(value))).isPresent();
    }

    @Test
    void likertRejectsNonIntegers() {
        Question q = question(spec(QuestionType.LIKERT, "R"));

        assertThat(AnswerValidator.validate(q, text("3"))).isPresent();
        assertThat(AnswerValidator.validate(q, SurveyTestData.JSON.numberNode(3.5))).isPresent();
        assertThat(AnswerValidator.validate(q, array("1"))).isPresent();
    }

    @Test
    void missingOrNullValueIsRejected() {
        Question q = question(spec(QuestionType.LIKERT, "R"));

        assertThat(AnswerValidator.validate(q, null)).hasValue("value is required");
        assertThat(AnswerValidator.validate(q, NullNode.getInstance())).hasValue("value is required");
    }

    // ---------- SINGLE_CHOICE / MINI_TEST ----------

    @Test
    void choiceAcceptsAnOptionIdOfThatQuestionOnly() {
        Question q = question(spec(QuestionType.SINGLE_CHOICE, "R", plainOptions(3)));
        String validId = q.getOptions().get(1).getId().toString();

        assertThat(AnswerValidator.validate(q, text(validId))).isEmpty();
        assertThat(AnswerValidator.validate(q, text(validId.toUpperCase()))).isEmpty();
        assertThat(AnswerValidator.validate(q, text(java.util.UUID.randomUUID().toString()))).isPresent();
        assertThat(AnswerValidator.validate(q, number(1))).isPresent();
        assertThat(AnswerValidator.validate(q, array(validId))).isPresent();
    }

    @Test
    void miniTestUsesTheSameRuleAsChoice() {
        Question q = question(spec(QuestionType.MINI_TEST, "LOGIC", List.of(
                SurveyTestData.option("a", null, false), SurveyTestData.option("b", null, true))));

        assertThat(AnswerValidator.validate(q, text(q.getOptions().get(0).getId().toString()))).isEmpty();
        assertThat(AnswerValidator.validate(q, text("nope"))).isPresent();
    }

    // ---------- RANKING ----------

    @Test
    void rankingNeedsEveryOptionExactlyOnce() {
        Question q = question(spec(QuestionType.RANKING, "VAL_INCOME", plainOptions(3)));
        String a = q.getOptions().get(0).getId().toString();
        String b = q.getOptions().get(1).getId().toString();
        String c = q.getOptions().get(2).getId().toString();

        assertThat(AnswerValidator.validate(q, array(c, a, b))).isEmpty();
        assertThat(AnswerValidator.validate(q, array(a, b))).isPresent();            // missing one
        assertThat(AnswerValidator.validate(q, array(a, a, b))).isPresent();         // duplicate
        assertThat(AnswerValidator.validate(q, array(a, b, java.util.UUID.randomUUID().toString()))).isPresent();
        assertThat(AnswerValidator.validate(q, array(a, b, c, a))).isPresent();      // too many
        assertThat(AnswerValidator.validate(q, text(a))).isPresent();                // not an array
    }

    // ---------- NUMERIC_INPUT ----------

    @Test
    void numericInputAcceptsAnyNumber() {
        Question q = question(spec(QuestionType.NUMERIC_INPUT, null));

        assertThat(AnswerValidator.validate(q, number(42))).isEmpty();
        assertThat(AnswerValidator.validate(q, SurveyTestData.JSON.numberNode(new BigDecimal("7.5")))).isEmpty();
        JsonNode notANumber = text("42");
        assertThat(AnswerValidator.validate(q, notANumber)).isPresent();
    }
}
