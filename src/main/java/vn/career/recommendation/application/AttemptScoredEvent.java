package vn.career.recommendation.application;

import java.util.UUID;

/**
 * Published inside the submit transaction; listeners annotated with AFTER_COMMIT only run once the results are
 * durable. The AI module uses it to write the explanations.
 */
public record AttemptScoredEvent(UUID attemptId, UUID userId) {
}
