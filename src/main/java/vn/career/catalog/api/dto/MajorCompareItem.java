package vn.career.catalog.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Side-by-side facts of one major. Cutoff and tuition ranges refer to {@code latestYear}. */
public record MajorCompareItem(
        UUID id,
        String code,
        String name,
        String groupName,
        String shortDescription,
        List<String> combos,
        List<String> typicalJobs,
        int universityCount,
        Integer latestYear,
        BigDecimal minCutoffScore,
        BigDecimal maxCutoffScore,
        Long minTuitionPerYear,
        Long maxTuitionPerYear,
        boolean sampleData) {
}
