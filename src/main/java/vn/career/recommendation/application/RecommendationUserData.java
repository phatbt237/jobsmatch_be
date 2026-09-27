package vn.career.recommendation.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.common.userdata.UserDataExporter;
import vn.career.recommendation.domain.Recommendation;
import vn.career.recommendation.infrastructure.RecommendationRepository;
import vn.career.recommendation.infrastructure.RecommendationSummaryRepository;
import vn.career.survey.application.SurveyAttemptApi;

/**
 * Personal data export of recommendations and their AI explanations. They are erased together with the attempts
 * by the survey module (ON DELETE CASCADE), so there is no eraser here.
 */
@Component
@RequiredArgsConstructor
class RecommendationUserData implements UserDataExporter {

    private final SurveyAttemptApi surveyApi;
    private final RecommendationRepository recommendations;
    private final RecommendationSummaryRepository summaries;
    private final CatalogQueryApi catalog;

    @Override
    public String section() {
        return "recommendations";
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportUserData(UUID userId) {
        return surveyApi.attemptIdsOf(userId).stream().map(attemptId -> {
            List<Recommendation> rows = recommendations.findByAttemptIdOrderByRankPosition(attemptId);
            Map<UUID, CatalogQueryApi.MajorSummary> majors = catalog.summaries(
                    rows.stream().map(Recommendation::getMajorId).collect(Collectors.toSet()));
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("attemptId", attemptId);
            map.put("summary", summaries.findById(attemptId).map(s -> s.getSummary()).orElse(null));
            map.put("majors", rows.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rank", r.getRankPosition());
                m.put("major", majors.containsKey(r.getMajorId()) ? majors.get(r.getMajorId()).name() : null);
                m.put("score", r.getScore());
                m.put("breakdown", r.getScoreBreakdown());
                m.put("explanation", r.getExplanation());
                m.put("explanationStatus", r.getExplanationStatus());
                m.put("algoVersion", r.getAlgoVersion());
                return m;
            }).toList());
            return map;
        }).toList();
    }
}
