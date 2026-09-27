package vn.career.content.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/**
 * Sections keys: a_day_at_work, wish_i_knew, dark_side, who_fits, school_vs_work (up to 5000 characters each).
 * A draft may leave some empty, all five must have text before the post is submitted for review.
 */
public record PovRequest(
        @NotNull UUID majorId,
        @NotBlank @Size(max = 255) String title,
        @NotNull @Size(max = 10) Map<String, String> sections,
        @Size(max = 500) @Pattern(regexp = "^https://.+", message = "must start with https://") String videoUrl) {
}
