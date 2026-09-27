package vn.career.survey.application;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.career.survey.domain.SurveyTestData.assignIds;
import static vn.career.survey.domain.SurveyTestData.spec;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.career.survey.api.dto.ProgressResponse;
import vn.career.survey.domain.Question;
import vn.career.survey.domain.QuestionSpec;
import vn.career.survey.domain.QuestionType;
import vn.career.survey.domain.Survey;
import vn.career.survey.domain.SurveySection;

class ProgressCalculatorTest {

    private final Survey survey = Survey.draft(1, "test");
    private final Question a1;
    private final Question a2;
    private final Question optionalA3;
    private final Question b1;

    ProgressCalculatorTest() {
        SurveySection a = survey.addSection(1, "A", "A", null);
        SurveySection b = survey.addSection(2, "B", "B", null);
        survey.addSection(3, "D", "Constraints", null);
        a1 = a.addQuestion(likert(1, true));
        a2 = a.addQuestion(likert(2, true));
        optionalA3 = a.addQuestion(likert(3, false));
        b1 = b.addQuestion(likert(1, true));
        List.of(a1, a2, optionalA3, b1).forEach(q -> assignIds(q));
    }

    private static QuestionSpec likert(int order, boolean required) {
        QuestionSpec base = spec(QuestionType.LIKERT, "R");
        return new QuestionSpec(base.type(), base.content(), base.dimensionCode(), base.weight(), false, false,
                null, required, order, List.of());
    }

    @Test
    void nothingAnsweredStartsAtTheFirstSection() {
        ProgressResponse progress = ProgressCalculator.calculate(survey, Set.of(), false);

        assertThat(progress.totalQuestions()).isEqualTo(4);
        assertThat(progress.requiredQuestions()).isEqualTo(3);
        assertThat(progress.answeredQuestions()).isZero();
        assertThat(progress.currentSectionCode()).isEqualTo("A");
        assertThat(progress.readyToSubmit()).isFalse();
    }

    @Test
    void optionalQuestionsDoNotBlockASection() {
        ProgressResponse progress = ProgressCalculator.calculate(survey, Set.of(a1.getId(), a2.getId()), false);

        assertThat(progress.sections().get(0).complete()).isTrue();
        assertThat(progress.sections().get(0).answeredQuestions()).isEqualTo(2);
        assertThat(progress.currentSectionCode()).isEqualTo("B");
    }

    @Test
    void constraintsSectionIsCompleteOnlyWhenConstraintsAreSaved() {
        Set<UUID> all = Set.of(a1.getId(), a2.getId(), b1.getId());

        ProgressResponse withoutConstraints = ProgressCalculator.calculate(survey, all, false);
        ProgressResponse withConstraints = ProgressCalculator.calculate(survey, all, true);

        assertThat(withoutConstraints.currentSectionCode()).isEqualTo("D");
        assertThat(withoutConstraints.readyToSubmit()).isFalse();
        assertThat(withConstraints.readyToSubmit()).isTrue();
        assertThat(withConstraints.currentSectionCode()).isEqualTo("D");
        assertThat(withConstraints.requiredAnswered()).isEqualTo(3);
    }

    @Test
    void surveyWithoutSectionsIsNeverReady() {
        ProgressResponse progress = ProgressCalculator.calculate(Survey.draft(2, "empty"), Set.of(), true);

        assertThat(progress.readyToSubmit()).isFalse();
        assertThat(progress.currentSectionCode()).isNull();
    }
}
