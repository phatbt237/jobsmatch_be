package vn.career.survey.application;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

/** What other modules may ask the survey module. Everything crosses the boundary as plain records. */
public interface SurveyAttemptApi {

    /** Basic facts about an attempt. Throws 404 if it does not exist. */
    AttemptInfo getAttempt(UUID attemptId);

    /**
     * Locks the attempt for submission: checks that {@code userId} owns it, that it is IN_PROGRESS and that every
     * required question (and section D when the survey has one) is done. Throws 403 / 404 / 422 otherwise.
     */
    AttemptInfo lockForSubmission(UUID userId, UUID attemptId);

    /** All questions of the attempt's survey version together with the student's answers. */
    ScoringData loadScoringData(UUID attemptId);

    Optional<ConstraintsData> loadConstraints(UUID attemptId);

    /** Marks the attempt SCORED and stores the submission time and the quality flags. */
    void markScored(UUID attemptId, Instant submittedAt, Map<String, Object> qualityFlags);

    /** Reference data: every dimension with its group, ordered by group then code. */
    List<DimensionInfo> dimensions();

    /** Ids of every attempt of a user, newest first. Used to export personal data. */
    List<UUID> attemptIdsOf(UUID userId);

    /**
     * Facts about an attempt. {@code status} is the AttemptStatus name (IN_PROGRESS, SCORED...);
     * {@code qualityFlags} is null until the attempt is scored.
     */
    record AttemptInfo(UUID attemptId, UUID userId, String status, int surveyVersion,
                       Instant startedAt, Instant submittedAt, Map<String, Object> qualityFlags) {
    }

    record DimensionInfo(String code, String name, String group) {
    }

    record ScoringOption(UUID id, int orderIndex, java.math.BigDecimal value, boolean correct) {
    }

    /** A question with the answer given to it, {@code answer} is null when unanswered. */
    record ScoringQuestion(UUID questionId, String type, String dimensionCode, java.math.BigDecimal weight,
                           boolean reverseScored, boolean attentionCheck,
                           com.fasterxml.jackson.databind.JsonNode expectedValue,
                           List<ScoringOption> options, com.fasterxml.jackson.databind.JsonNode answer) {
    }

    record ScoringData(UUID attemptId, Instant startedAt, List<ScoringQuestion> questions) {
    }

    record ConstraintsData(Map<String, java.math.BigDecimal> gpa, List<String> combos,
                           List<String> preferredRegions, Long budgetPerYear, Integer familyPressure) {
    }
}
