package vn.career.survey.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A batch of answers to save (autosave). Saving the same question again overwrites the previous answer. */
public record SaveAnswersRequest(@NotEmpty @Size(max = 200) List<@Valid AnswerItem> answers) {
}
