package vn.career.catalog.api.dto;

import java.math.BigDecimal;

public record YearDataResponse(int year, String combo, BigDecimal cutoffScore, Long tuitionPerYear) {
}
