package vn.career.content.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A published post as anybody can read it. {@code sampleData} is true for invented development content. */
public record PovResponse(
        UUID id,
        UUID majorId,
        String title,
        Map<String, String> sections,
        String videoUrl,
        Instant publishedAt,
        MentorSummary mentor,
        boolean sampleData) {
}
