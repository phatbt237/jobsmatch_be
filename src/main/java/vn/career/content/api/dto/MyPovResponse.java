package vn.career.content.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.career.content.domain.PovStatus;

/** A post as its author sees it, including the review state and the reason for a rejection. */
public record MyPovResponse(
        UUID id,
        UUID majorId,
        String title,
        Map<String, String> sections,
        String videoUrl,
        PovStatus status,
        String rejectReason,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {
}
