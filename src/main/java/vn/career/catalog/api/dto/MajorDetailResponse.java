package vn.career.catalog.api.dto;

import java.util.List;
import java.util.UUID;

public record MajorDetailResponse(
        UUID id,
        String code,
        String name,
        String groupName,
        String shortDescription,
        String description,
        List<String> combos,
        List<String> typicalJobs,
        boolean sampleData,
        List<UniversityOfferingResponse> universities) {
}
