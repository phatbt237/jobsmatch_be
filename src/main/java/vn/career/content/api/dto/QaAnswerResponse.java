package vn.career.content.api.dto;

import java.time.Instant;
import java.util.UUID;

public record QaAnswerResponse(
        UUID id,
        UUID threadId,
        String content,
        String authorRole,
        String authorLabel,
        boolean mentorAnswer,
        Instant createdAt) {
}
