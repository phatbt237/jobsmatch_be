package vn.career.catalog.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;
import vn.career.catalog.domain.Region;

/** One row of the latest-year admission data of a major, used by the matcher. */
public record OfferingRow(UUID majorId, Region region, Long tuitionPerYear, BigDecimal cutoffScore,
                          int year, String combo) {
}
