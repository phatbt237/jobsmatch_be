package vn.career.catalog.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * A university that offers the major with its data for the most recent years. {@code sampleData} is true for
 * made-up development data, clients must not present those figures as real.
 */
public record UniversityOfferingResponse(
        UUID universityId,
        String code,
        String name,
        String region,
        String type,
        boolean sampleData,
        List<YearDataResponse> years) {
}
