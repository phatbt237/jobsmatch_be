package vn.career.content.api.dto;

import jakarta.validation.constraints.NotNull;
import vn.career.content.domain.ContentStatus;

public record ModerationRequest(@NotNull ContentStatus status) {
}
