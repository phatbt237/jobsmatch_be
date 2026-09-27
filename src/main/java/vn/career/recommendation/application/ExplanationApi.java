package vn.career.recommendation.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the AI module needs from the recommendation module to write and store explanations. */
public interface ExplanationApi {

    /** Everything the LLM is allowed to see. Empty if the attempt has no recommendations. */
    ExplanationInput loadInput(UUID attemptId);

    /** Stores the texts (keyed by major id) and the summary, marks them DONE and pushes the result to SSE clients. */
    void saveExplanation(UUID attemptId, String summary, Map<UUID, String> textsByMajorId, String model);

    /** Marks the still pending explanations FAILED (the recommendations themselves stay valid). */
    void markFailed(UUID attemptId);

    record ExplanationInput(UUID attemptId, boolean lowReliability, List<DimensionLine> dimensions,
                            List<MajorLine> majors) {
    }

    /** A dimension with the student's score as a percentage 0..100. */
    record DimensionLine(String code, String name, String group, double percent) {
    }

    /** A recommended major with its score and the numbers behind it. */
    record MajorLine(UUID majorId, String name, int rank, double score, Map<String, Object> breakdown) {
    }
}
