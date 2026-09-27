package vn.career.survey.api.dto;

public record SectionProgress(
        String code,
        int totalQuestions,
        int answeredQuestions,
        int requiredQuestions,
        int requiredAnswered,
        boolean complete) {
}
