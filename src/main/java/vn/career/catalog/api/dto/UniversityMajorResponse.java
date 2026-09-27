package vn.career.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UniversityMajorResponse(
        UUID id,
        UUID universityId,
        String universityName,
        UUID majorId,
        String majorName,
        int year,
        String combo,
        BigDecimal cutoffScore,
        Long tuitionPerYear,
        boolean sampleData) {
}
