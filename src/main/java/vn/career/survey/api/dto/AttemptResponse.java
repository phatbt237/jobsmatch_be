package vn.career.survey.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.career.survey.domain.AttemptStatus;

public record AttemptResponse(
        UUID id,
        UUID surveyId,
        int surveyVersion,
        AttemptStatus status,
        Instant startedAt,
        Instant submittedAt) {
}
