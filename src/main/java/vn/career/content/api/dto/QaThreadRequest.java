package vn.career.content.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QaThreadRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 5000) String content) {
}
