package vn.career.catalog.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** What other modules may ask the catalog module. Plain records only, no entities. */
public interface CatalogQueryApi {

    boolean majorExists(UUID majorId);

    List<UUID> activeMajorIds();

    /** Names and codes for the given majors, keyed by id (unknown ids are left out). */
    Map<UUID, MajorSummary> summaries(Collection<UUID> majorIds);

    /** Every active major with its profile weights and its most recent admission data, for the matcher. */
    List<MajorMatchData> loadActiveMajorsForMatching();

    /** Text of a major for the search index, or empty if the major does not exist or is inactive. */
    Optional<MajorDocument> majorDocument(UUID majorId);

    record MajorSummary(UUID id, String code, String name, String groupName) {
    }

    /** {@code region} is NORTH, CENTRAL or SOUTH. Rows are from the most recent year that has data for the major. */
    record Offering(String region, Long tuitionPerYear, BigDecimal cutoffScore, int year, String combo) {
    }

    record MajorMatchData(UUID id, String code, String name, boolean active, List<String> combos,
                          Map<String, BigDecimal> profile, List<Offering> offerings) {
    }

    record MajorDocument(UUID id, String name, String text) {
    }
}
