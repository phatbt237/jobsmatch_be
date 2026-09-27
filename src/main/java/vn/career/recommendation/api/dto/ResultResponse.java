package vn.career.recommendation.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The outcome of a scored attempt. {@code explanationStatus} is PENDING (still being written), DONE, FAILED
 * (recommendations are still valid, just without text) or NONE (there was nothing to explain).
 * {@code lowReliability} suggests the student retakes the survey.
 */
public record ResultResponse(
        UUID attemptId,
        String attemptStatus,
        Instant submittedAt,
        boolean lowReliability,
        Map<String, Object> qualityFlags,
        List<DimensionScoreResponse> dimensions,
        List<RecommendationResponse> recommendations,
        String explanationStatus,
        String summary,
        String algoVersion,
        String disclaimer) {
}
