package vn.career.content.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code authorLabel} is a role based label ("Học sinh", "Phụ huynh", or a mentor's job title), never a real name:
 * many users are minors.
 */
public record QaThreadResponse(
        UUID id,
        UUID majorId,
        String title,
        String content,
        String authorRole,
        String authorLabel,
        Instant createdAt,
        long answerCount) {
}
