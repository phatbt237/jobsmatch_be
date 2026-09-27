package vn.career.scoring.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.userdata.UserDataExporter;
import vn.career.survey.application.SurveyAttemptApi;

/**
 * Personal data export of the dimension scores. Nothing to erase here: the scores are deleted together with the
 * attempts by the survey module (foreign key with ON DELETE CASCADE).
 */
@Component
@RequiredArgsConstructor
class ScoringUserData implements UserDataExporter {

    private final SurveyAttemptApi surveyApi;
    private final ScoringService scoringService;

    @Override
    public String section() {
        return "dimensionScores";
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportUserData(UUID userId) {
        return surveyApi.attemptIdsOf(userId).stream().map(attemptId -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("attemptId", attemptId);
            List<Map<String, Object>> scores = scoringService.getScores(attemptId).stream().map(s -> {
                Map<String, Object> score = new LinkedHashMap<>();
                score.put("dimension", s.code());
                score.put("raw", s.raw());
                score.put("normalized", s.normalized());
                return score;
            }).toList();
            map.put("scores", scores);
            return map;
        }).toList();
    }
}
