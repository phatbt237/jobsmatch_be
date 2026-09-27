package vn.career.scoring.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.scoring.domain.DimensionScore;
import vn.career.scoring.domain.DimensionScorer;
import vn.career.scoring.domain.OptionInput;
import vn.career.scoring.domain.QualityChecker;
import vn.career.scoring.domain.QualityFlags;
import vn.career.scoring.domain.ScoringItem;
import vn.career.scoring.infrastructure.AttemptScoreStore;
import vn.career.survey.application.SurveyAttemptApi;

/** Scores an attempt: reads the answers from the survey module, stores the dimension scores, checks data quality. */
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final SurveyAttemptApi surveyApi;
    private final AttemptScoreStore store;
    private final ScoringProperties properties;
    private final DimensionScorer scorer = new DimensionScorer();

    /** Runs inside the caller's transaction so scoring commits or rolls back together with the submission. */
    @Transactional
    public ScoringResult scoreAttempt(UUID attemptId, Instant submittedAt) {
        SurveyAttemptApi.ScoringData data = surveyApi.loadScoringData(attemptId);
        List<ScoringItem> items = data.questions().stream().map(ScoringService::toItem).toList();

        Map<String, DimensionScore> scores = scorer.score(items);
        store.replace(attemptId, List.copyOf(scores.values()));

        QualityFlags flags = new QualityChecker(properties.attentionFailThreshold(), properties.minSecondsPerQuestion())
                .check(items, Duration.between(data.startedAt(), submittedAt));
        List<DimensionResult> results = scores.values().stream()
                .map(s -> new DimensionResult(s.dimension(), s.raw(), s.normalized()))
                .toList();
        return new ScoringResult(results, flags.toMap(), flags.lowReliability());
    }

    @Transactional(readOnly = true)
    public List<DimensionResult> getScores(UUID attemptId) {
        return store.find(attemptId);
    }

    private static ScoringItem toItem(SurveyAttemptApi.ScoringQuestion q) {
        List<OptionInput> options = q.options().stream()
                .map(o -> new OptionInput(o.id().toString(), o.orderIndex(),
                        o.value() == null ? null : o.value().doubleValue(), o.correct()))
                .toList();
        JsonNode expected = q.expectedValue();
        return new ScoringItem(q.type(), q.dimensionCode(), q.weight().doubleValue(), q.reverseScored(),
                q.attentionCheck(), expected, options, q.answer());
    }
}
