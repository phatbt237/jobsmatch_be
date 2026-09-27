package vn.career.survey.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.career.survey.api.dto.ProgressResponse;
import vn.career.survey.api.dto.SectionProgress;
import vn.career.survey.domain.Question;
import vn.career.survey.domain.Survey;
import vn.career.survey.domain.SurveySection;

/** Works out how far a student got through a survey. */
final class ProgressCalculator {

    /** By convention section D holds the practical constraints, saved through its own endpoint. */
    static final String CONSTRAINTS_SECTION_CODE = "D";

    private ProgressCalculator() {
    }

    static ProgressResponse calculate(Survey survey, Set<UUID> answeredQuestionIds, boolean constraintsSaved) {
        List<SectionProgress> sections = new ArrayList<>();
        int total = 0;
        int answered = 0;
        int required = 0;
        int requiredAnswered = 0;

        for (SurveySection section : survey.getSections()) {
            int sectionTotal = section.getQuestions().size();
            int sectionAnswered = 0;
            int sectionRequired = 0;
            int sectionRequiredAnswered = 0;
            for (Question question : section.getQuestions()) {
                boolean isAnswered = answeredQuestionIds.contains(question.getId());
                sectionAnswered += isAnswered ? 1 : 0;
                if (question.isRequired()) {
                    sectionRequired++;
                    sectionRequiredAnswered += isAnswered ? 1 : 0;
                }
            }
            boolean complete = sectionRequiredAnswered == sectionRequired
                    && (!CONSTRAINTS_SECTION_CODE.equals(section.getCode()) || constraintsSaved);
            sections.add(new SectionProgress(section.getCode(), sectionTotal, sectionAnswered,
                    sectionRequired, sectionRequiredAnswered, complete));
            total += sectionTotal;
            answered += sectionAnswered;
            required += sectionRequired;
            requiredAnswered += sectionRequiredAnswered;
        }

        boolean allComplete = !sections.isEmpty() && sections.stream().allMatch(SectionProgress::complete);
        String current = sections.stream().filter(s -> !s.complete()).map(SectionProgress::code).findFirst()
                .orElse(sections.isEmpty() ? null : sections.get(sections.size() - 1).code());
        return new ProgressResponse(total, answered, required, requiredAnswered, constraintsSaved, current,
                allComplete, sections);
    }
}
