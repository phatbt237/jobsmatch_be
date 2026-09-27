package vn.career.content.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.career.content.domain.PovStatus;

public record AdminPovResponse(
        UUID id,
        UUID mentorId,
        MentorSummary mentor,
        boolean mentorVerified,
        UUID majorId,
        String title,
        Map<String, String> sections,
        String videoUrl,
        PovStatus status,
        String rejectReason,
        Instant publishedAt,
        Instant updatedAt,
        boolean sampleData) {
}
