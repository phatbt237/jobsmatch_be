package vn.career.catalog.api.dto;

import java.util.List;
import java.util.UUID;

public record MajorSummaryResponse(
        UUID id,
        String code,
        String name,
        String groupName,
        String shortDescription,
        List<String> combos) {
}
