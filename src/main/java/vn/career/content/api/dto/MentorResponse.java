package vn.career.content.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Full mentor profile, only shown to the mentor and to admins. */
public record MentorResponse(
        UUID userId,
        String company,
        String jobTitle,
        int yearsExperience,
        UUID majorId,
        String bio,
        String linkedinUrl,
        boolean verified,
        Instant verifiedAt,
        boolean sampleData) {
}
