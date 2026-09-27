package vn.career.content.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.career.content.domain.PovStatus;

/** Review decision: PUBLISHED or REJECTED. A rejection must carry a reason the mentor can act on. */
public record PovStatusRequest(@NotNull PovStatus status, @Size(max = 2000) String reason) {
}
