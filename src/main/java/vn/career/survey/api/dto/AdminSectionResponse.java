package vn.career.survey.api.dto;

import java.util.List;
import java.util.UUID;

public record AdminSectionResponse(
        UUID id,
        int orderIndex,
        String code,
        String title,
        String description,
        List<AdminQuestionResponse> questions) {
}
