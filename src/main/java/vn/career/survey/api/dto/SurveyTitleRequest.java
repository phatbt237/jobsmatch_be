package vn.career.survey.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SurveyTitleRequest(@NotBlank @Size(max = 255) String title) {
}
