package vn.career.survey.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.userdata.UserDataEraser;
import vn.career.common.userdata.UserDataExporter;
import vn.career.survey.domain.Answer;
import vn.career.survey.domain.SurveyAttempt;
import vn.career.survey.infrastructure.AnswerRepository;
import vn.career.survey.infrastructure.StudentConstraintsRepository;
import vn.career.survey.infrastructure.SurveyAttemptRepository;

/**
 * Survey part of "delete my account" and "export my data". Deleting the attempts also removes what hangs off them
 * (answers, dimension scores, constraints, recommendations and their summaries) through ON DELETE CASCADE.
 */
@Component
@RequiredArgsConstructor
class SurveyUserData implements UserDataEraser, UserDataExporter {

    private final SurveyAttemptRepository attempts;
    private final AnswerRepository answers;
    private final StudentConstraintsRepository constraints;

    @Override
    @Transactional
    public void eraseUserData(UUID userId) {
        attempts.deleteAllByUserId(userId);
    }

    @Override
    public String section() {
        return "surveyAttempts";
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportUserData(UUID userId) {
        return attempts.findAllByUserIdOrderByStartedAtDesc(userId).stream().map(this::export).toList();
    }

    private Map<String, Object> export(SurveyAttempt attempt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("attemptId", attempt.getId());
        map.put("surveyVersion", attempt.getSurvey().getVersion());
        map.put("status", attempt.getStatus());
        map.put("startedAt", attempt.getStartedAt());
        map.put("submittedAt", attempt.getSubmittedAt());
        map.put("qualityFlags", attempt.getQualityFlags());
        List<Map<String, Object>> answerList = answers.findAllOfAttempt(attempt.getId()).stream().map(SurveyUserData::export).toList();
        map.put("answers", answerList);
        map.put("constraints", constraints.findById(attempt.getId()).map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gpa", c.getGpa());
            m.put("combos", c.getCombos());
            m.put("preferredRegions", c.getPreferredRegions());
            m.put("budgetPerYear", c.getBudgetPerYear());
            m.put("familyPressure", c.getFamilyPressure());
            return m;
        }).orElse(null));
        return map;
    }

    private static Map<String, Object> export(Answer answer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("question", answer.getQuestion().getContent());
        map.put("value", answer.getValue());
        map.put("answeredAt", answer.getAnsweredAt());
        return map;
    }
}
