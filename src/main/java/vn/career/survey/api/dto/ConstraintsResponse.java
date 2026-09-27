package vn.career.survey.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ConstraintsResponse(
        Map<String, BigDecimal> gpa,
        List<String> combos,
        List<String> preferredRegions,
        Long budgetPerYear,
        Integer familyPressure) {
}
