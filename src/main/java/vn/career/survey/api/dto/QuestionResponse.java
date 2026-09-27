package vn.career.survey.api.dto;

import java.util.List;
import java.util.UUID;
import vn.career.survey.domain.QuestionType;

/** Question as seen by a student: no dimension, weight, reverse flag, attention flag or expected value. */
public record QuestionResponse(
        UUID id,
        int orderIndex,
        QuestionType type,
        String content,
        boolean required,
        List<OptionResponse> options) {
}
