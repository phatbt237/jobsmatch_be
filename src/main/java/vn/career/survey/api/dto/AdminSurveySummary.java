package vn.career.survey.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.career.survey.domain.SurveyStatus;

public record AdminSurveySummary(
        UUID id,
        int version,
        String title,
        SurveyStatus status,
        Instant publishedAt,
        long sectionCount,
        long questionCount) {
}
