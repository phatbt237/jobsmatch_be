package vn.career.recommendation.api.dto;

import java.util.Map;
import java.util.UUID;

/**
 * One recommended major. {@code score} is 0..1, {@code breakdown} explains it (interest, aptitude, values, penalties).
 * {@code explanation} is null while {@code explanationStatus} is PENDING or FAILED.
 */
public record RecommendationResponse(
        int rank,
        UUID majorId,
        String majorCode,
        String majorName,
        String groupName,
        double score,
        Map<String, Object> breakdown,
        String explanation,
        String explanationStatus) {
}
