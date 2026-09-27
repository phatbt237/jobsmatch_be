package vn.career.survey.api.dto;

import java.util.List;

/** Everything needed to resume an attempt: progress, the answers saved so far and section D. */
public record AttemptDetailResponse(
        AttemptResponse attempt,
        ProgressResponse progress,
        List<SavedAnswer> answers,
        ConstraintsResponse constraints) {
}
