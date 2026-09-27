package vn.career.recommendation.api.dto;

import java.time.Instant;
import java.util.UUID;

/** One finished survey of a student, enough for a parent to pick which result to open. */
public record ScoredAttemptSummary(UUID attemptId, Instant submittedAt, int surveyVersion) {
}
