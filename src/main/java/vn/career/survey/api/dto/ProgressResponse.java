package vn.career.survey.api.dto;

import java.util.List;

/**
 * How far a student got. Section D (practical constraints) counts as complete once its constraints are saved.
 * {@code currentSectionCode} is the first incomplete section, or the last section when everything is done.
 */
public record ProgressResponse(
        int totalQuestions,
        int answeredQuestions,
        int requiredQuestions,
        int requiredAnswered,
        boolean constraintsSaved,
        String currentSectionCode,
        boolean readyToSubmit,
        List<SectionProgress> sections) {
}
