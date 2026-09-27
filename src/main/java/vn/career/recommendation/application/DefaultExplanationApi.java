package vn.career.recommendation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.event.AfterCommit;
import vn.career.recommendation.api.dto.ResultResponse;
import vn.career.recommendation.domain.ExplanationStatus;
import vn.career.recommendation.domain.Recommendation;
import vn.career.recommendation.domain.RecommendationSummary;
import vn.career.recommendation.infrastructure.RecommendationRepository;
import vn.career.recommendation.infrastructure.RecommendationSummaryRepository;

@Service
@RequiredArgsConstructor
class DefaultExplanationApi implements ExplanationApi {

    private final RecommendationRepository recommendations;
    private final RecommendationSummaryRepository summaries;
    private final ResultService resultService;
    private final ResultStreamRegistry streams;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ExplanationInput loadInput(UUID attemptId) {
        ResultResponse result = resultService.buildResult(attemptId);
        List<DimensionLine> dimensions = result.dimensions().stream()
                .map(d -> new DimensionLine(d.code(), d.name(), d.group(), d.percent()))
                .toList();
        List<MajorLine> majors = result.recommendations().stream()
                .map(r -> new MajorLine(r.majorId(), r.majorName(), r.rank(), r.score(), r.breakdown()))
                .toList();
        return new ExplanationInput(attemptId, result.lowReliability(), dimensions, majors);
    }

    @Override
    @Transactional
    public void saveExplanation(UUID attemptId, String summary, Map<UUID, String> textsByMajorId, String model) {
        for (Recommendation r : recommendations.findByAttemptIdOrderByRankPosition(attemptId)) {
            String text = textsByMajorId.get(r.getMajorId());
            if (text != null) {
                r.explain(text, model);
            } else {
                r.markFailed();
            }
        }
        summaries.findById(attemptId).ifPresentOrElse(
                existing -> existing.rewrite(summary),
                () -> summaries.save(new RecommendationSummary(attemptId, summary, Instant.now(clock))));
        pushWhenCommitted(attemptId);
    }

    // REQUIRES_NEW: may be called from an AFTER_COMMIT callback, where joining the finished transaction would lose the change.
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markFailed(UUID attemptId) {
        recommendations.findByAttemptIdOrderByRankPosition(attemptId).stream()
                .filter(r -> r.getExplanationStatus() == ExplanationStatus.PENDING)
                .forEach(Recommendation::markFailed);
        pushWhenCommitted(attemptId);
    }

    private void pushWhenCommitted(UUID attemptId) {
        AfterCommit.run(() -> streams.publish(attemptId, resultService.buildResult(attemptId)));
    }
}
