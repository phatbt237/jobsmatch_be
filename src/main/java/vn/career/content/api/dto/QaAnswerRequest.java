package vn.career.content.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QaAnswerRequest(@NotBlank @Size(max = 5000) String content) {
}
