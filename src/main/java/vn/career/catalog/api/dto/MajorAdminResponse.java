package vn.career.catalog.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Full major incl. inactive state and the dimension profile used by the matcher. */
public record MajorAdminResponse(
        UUID id,
        String code,
        String name,
        String groupName,
        String shortDescription,
        String description,
        List<String> combos,
        List<String> typicalJobs,
        boolean active,
        Map<String, BigDecimal> profile) {
}
