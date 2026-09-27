package vn.career.survey.api.dto;

import java.util.List;
import java.util.UUID;

/** The active survey for students. {@code likertScale} tells the client how to label LIKERT answers 1..5. */
public record SurveyResponse(
        UUID id,
        int version,
        String title,
        List<LikertOption> likertScale,
        List<SectionResponse> sections) {
}
