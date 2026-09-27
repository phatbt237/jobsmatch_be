package vn.career.survey.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.career.survey.domain.SurveyStatus;

public record AdminSurveyResponse(
        UUID id,
        int version,
        String title,
        SurveyStatus status,
        Instant publishedAt,
        List<AdminSectionResponse> sections) {
}
