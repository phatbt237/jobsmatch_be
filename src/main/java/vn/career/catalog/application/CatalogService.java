package vn.career.catalog.application;

import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.catalog.api.dto.MajorCompareItem;
import vn.career.catalog.api.dto.MajorCompareResponse;
import vn.career.catalog.api.dto.MajorDetailResponse;
import vn.career.catalog.api.dto.MajorSummaryResponse;
import vn.career.catalog.api.dto.UniversityOfferingResponse;
import vn.career.catalog.api.dto.UniversityResponse;
import vn.career.catalog.api.dto.YearDataResponse;
import vn.career.catalog.domain.Major;
import vn.career.catalog.domain.Region;
import vn.career.catalog.domain.University;
import vn.career.catalog.domain.UniversityMajor;
import vn.career.catalog.infrastructure.CatalogCache;
import vn.career.catalog.infrastructure.MajorRepository;
import vn.career.catalog.infrastructure.UniversityMajorRepository;
import vn.career.catalog.infrastructure.UniversityRepository;
import vn.career.common.api.ErrorDetail;
import vn.career.common.api.PageRequests;
import vn.career.common.api.PageResponse;
import vn.career.common.exception.AppException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.NotFoundException;

/** Public, read-only catalog. Responses are cached in Redis until an admin changes the catalog. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    static final int MAX_COMPARE = 3;
    private static final int YEARS_SHOWN = 3;

    private final MajorRepository majors;
    private final UniversityRepository universities;
    private final UniversityMajorRepository universityMajors;
    private final CatalogCache cache;

    @Transactional(readOnly = true)
    public PageResponse<MajorSummaryResponse> searchMajors(String group, String combo, String q, Integer page, Integer size) {
        String groupFilter = blankToNull(group);
        String comboFilter = blankToNull(combo) == null ? null : combo.trim().toUpperCase(Locale.ROOT);
        String pattern = blankToNull(q) == null ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequests.of(page, size, null);
        String key = "majors|" + groupFilter + "|" + comboFilter + "|" + pattern + "|" + pageable.getPageNumber()
                + "|" + pageable.getPageSize();
        return cache.getOrLoad(key, new TypeReference<PageResponse<MajorSummaryResponse>>() { }, () ->
                PageResponse.from(majors.search(groupFilter, pattern, comboFilter, pageable).map(this::toSummary)));
    }

    @Transactional(readOnly = true)
    public MajorDetailResponse getMajor(UUID id) {
        return cache.getOrLoad("major|" + id, new TypeReference<MajorDetailResponse>() { }, () -> loadDetail(id));
    }

    @Transactional(readOnly = true)
    public MajorCompareResponse compare(List<UUID> ids) {
        List<UUID> distinct = ids == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(ids));
        if (distinct.isEmpty() || distinct.size() > MAX_COMPARE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new ErrorDetail("ids", "provide between 1 and " + MAX_COMPARE + " different major ids")));
        }
        String key = "compare|" + distinct;
        return cache.getOrLoad(key, new TypeReference<MajorCompareResponse>() { }, () ->
                new MajorCompareResponse(distinct.stream().map(this::loadCompareItem).toList()));
    }

    @Transactional(readOnly = true)
    public List<UniversityResponse> listUniversities(String region) {
        Region filter = parseRegion(region);
        return cache.getOrLoad("universities|" + filter, new TypeReference<List<UniversityResponse>>() { }, () -> {
            List<University> found = filter == null
                    ? universities.findAllByOrderByName() : universities.findByRegionOrderByName(filter);
            return found.stream().map(CatalogService::toUniversityResponse).toList();
        });
    }

    // ---------------------------------------------------------------- loaders

    private MajorDetailResponse loadDetail(UUID id) {
        Major major = majors.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MAJOR_NOT_FOUND, "Major not found"));
        List<UniversityMajor> rows = recentRows(id);
        List<UniversityOfferingResponse> offerings = new ArrayList<>();
        UniversityOfferingResponse current = null;
        UUID currentUniversity = null;
        List<YearDataResponse> years = null;
        boolean sample = false;
        for (UniversityMajor row : rows) {
            University u = row.getUniversity();
            if (!u.getId().equals(currentUniversity)) {
                years = new ArrayList<>();
                current = new UniversityOfferingResponse(u.getId(), u.getCode(), u.getName(), u.getRegion().name(),
                        u.getType().name(), u.isSample(), years);
                offerings.add(current);
                currentUniversity = u.getId();
            }
            years.add(new YearDataResponse(row.getYear(), row.getCombo(), row.getCutoffScore(), row.getTuitionPerYear()));
            sample |= row.isSample() || u.isSample();
        }
        return new MajorDetailResponse(major.getId(), major.getCode(), major.getName(), major.getGroupName(),
                major.getShortDescription(), major.getDescription(), List.of(major.getCombos()),
                List.of(major.getTypicalJobs()), sample, offerings);
    }

    private MajorCompareItem loadCompareItem(UUID id) {
        Major major = majors.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MAJOR_NOT_FOUND, "Major not found: " + id));
        List<UniversityMajor> rows = recentRows(id);
        Integer latest = rows.stream().map(UniversityMajor::getYear).max(Comparator.naturalOrder()).orElse(null);
        List<UniversityMajor> latestRows = rows.stream().filter(r -> latest != null && r.getYear() == latest).toList();
        BigDecimal minCutoff = latestRows.stream().map(UniversityMajor::getCutoffScore).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        BigDecimal maxCutoff = latestRows.stream().map(UniversityMajor::getCutoffScore).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        Long minTuition = latestRows.stream().map(UniversityMajor::getTuitionPerYear).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        Long maxTuition = latestRows.stream().map(UniversityMajor::getTuitionPerYear).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        long universityCount = latestRows.stream().map(r -> r.getUniversity().getId()).distinct().count();
        boolean sample = rows.stream().anyMatch(r -> r.isSample() || r.getUniversity().isSample());
        return new MajorCompareItem(major.getId(), major.getCode(), major.getName(), major.getGroupName(),
                major.getShortDescription(), List.of(major.getCombos()), List.of(major.getTypicalJobs()),
                (int) universityCount, latest, minCutoff, maxCutoff, minTuition, maxTuition, sample);
    }

    /** Admission rows of the three most recent years that have data for the major. */
    private List<UniversityMajor> recentRows(UUID majorId) {
        List<Integer> years = universityMajors.findYears(majorId).stream().limit(YEARS_SHOWN).toList();
        return years.isEmpty() ? List.of() : universityMajors.findForMajor(majorId, years);
    }

    private MajorSummaryResponse toSummary(Major major) {
        return new MajorSummaryResponse(major.getId(), major.getCode(), major.getName(), major.getGroupName(),
                major.getShortDescription(), List.of(major.getCombos()));
    }

    static UniversityResponse toUniversityResponse(University u) {
        return new UniversityResponse(u.getId(), u.getCode(), u.getName(), u.getRegion().name(), u.getType().name(),
                u.isSample());
    }

    private static Region parseRegion(String region) {
        if (blankToNull(region) == null) {
            return null;
        }
        try {
            return Region.valueOf(region.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new ErrorDetail("region", "must be one of NORTH, CENTRAL, SOUTH")));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
