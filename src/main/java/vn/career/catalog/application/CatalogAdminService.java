package vn.career.catalog.application;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.catalog.api.dto.MajorAdminResponse;
import vn.career.catalog.api.dto.MajorRequest;
import vn.career.catalog.api.dto.ProfileRequest;
import vn.career.catalog.api.dto.UniversityMajorRequest;
import vn.career.catalog.api.dto.UniversityMajorResponse;
import vn.career.catalog.api.dto.UniversityRequest;
import vn.career.catalog.api.dto.UniversityResponse;
import vn.career.catalog.domain.Major;
import vn.career.catalog.domain.MajorProfile;
import vn.career.catalog.domain.University;
import vn.career.catalog.domain.UniversityMajor;
import vn.career.catalog.infrastructure.CatalogCache;
import vn.career.catalog.infrastructure.MajorProfileRepository;
import vn.career.catalog.infrastructure.MajorRepository;
import vn.career.catalog.infrastructure.UniversityMajorRepository;
import vn.career.catalog.infrastructure.UniversityRepository;
import vn.career.common.api.ErrorDetail;
import vn.career.common.api.PageResponse;
import vn.career.common.event.DomainEventPublisher;
import vn.career.common.exception.AppException;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.NotFoundException;
import vn.career.survey.application.SurveyAttemptApi;

/**
 * Admin management of the catalog. Every change clears the public cache once it is committed, and changes to a
 * major publish {@link MajorChangedEvent} so the search index can follow.
 */
@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private final MajorRepository majors;
    private final MajorProfileRepository profiles;
    private final UniversityRepository universities;
    private final UniversityMajorRepository universityMajors;
    private final SurveyAttemptApi surveyApi;
    private final CatalogCache cache;
    private final DomainEventPublisher events;

    // ---------------------------------------------------------------- majors

    @Transactional(readOnly = true)
    public PageResponse<MajorAdminResponse> listMajors(String q, Pageable pageable) {
        Pageable sorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name"));
        Page<Major> page = q == null || q.isBlank()
                ? majors.findAll(sorted) : majors.findByNameContainingIgnoreCase(q.trim(), sorted);
        return PageResponse.from(page.map(this::toAdminResponse));
    }

    @Transactional(readOnly = true)
    public MajorAdminResponse getMajor(UUID id) {
        return toAdminResponse(findMajor(id));
    }

    @Transactional
    public MajorAdminResponse createMajor(MajorRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new ErrorDetail("code", "is required when creating a major")));
        }
        if (majors.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE, "A major with this code already exists");
        }
        Major major = majors.save(Major.create(request.code(), request.name().trim(), request.groupName().trim(),
                request.shortDescription(), request.description(), toArray(request.combos()),
                toArray(request.typicalJobs()), request.active() == null || request.active()));
        changed(major.getId());
        return toAdminResponse(major);
    }

    @Transactional
    public MajorAdminResponse updateMajor(UUID id, MajorRequest request) {
        Major major = findMajor(id);
        major.update(request.name().trim(), request.groupName().trim(), request.shortDescription(),
                request.description(), toArray(request.combos()), toArray(request.typicalJobs()),
                request.active() == null || request.active());
        majors.flush();
        changed(id);
        return toAdminResponse(major);
    }

    /** Majors are never physically deleted (recommendations refer to them); deleting deactivates. */
    @Transactional
    public void deactivateMajor(UUID id) {
        findMajor(id).deactivate();
        majors.flush();
        changed(id);
    }

    @Transactional
    public MajorAdminResponse setProfile(UUID id, ProfileRequest request) {
        Major major = findMajor(id);
        Set<String> known = surveyApi.dimensions().stream()
                .map(SurveyAttemptApi.DimensionInfo::code).collect(Collectors.toSet());
        List<ErrorDetail> unknown = request.weights().keySet().stream()
                .filter(code -> !known.contains(code))
                .map(code -> new ErrorDetail("weights." + code, "unknown dimension"))
                .toList();
        if (!unknown.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed", unknown);
        }
        profiles.deleteByMajorId(id);
        profiles.flush();
        profiles.saveAll(request.weights().entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(e -> new MajorProfile(id, e.getKey(), e.getValue()))
                .toList());
        profiles.flush();
        changed(id);
        return toAdminResponse(major);
    }

    // ---------------------------------------------------------------- universities

    @Transactional(readOnly = true)
    public PageResponse<UniversityResponse> listUniversities(Pageable pageable) {
        return PageResponse.from(universities.findAllByOrderByName(pageable)
                .map(CatalogService::toUniversityResponse));
    }

    @Transactional
    public UniversityResponse createUniversity(UniversityRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new ErrorDetail("code", "is required when creating a university")));
        }
        if (universities.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE, "A university with this code already exists");
        }
        University university = universities.save(
                University.create(request.code(), request.name().trim(), request.region(), request.type()));
        cache.evictAll();
        return CatalogService.toUniversityResponse(university);
    }

    @Transactional
    public UniversityResponse updateUniversity(UUID id, UniversityRequest request) {
        University university = findUniversity(id);
        university.update(request.name().trim(), request.region(), request.type());
        universities.flush();
        cache.evictAll();
        return CatalogService.toUniversityResponse(university);
    }

    @Transactional
    public void deleteUniversity(UUID id) {
        universities.delete(findUniversity(id));
        universities.flush();
        cache.evictAll();
    }

    // ---------------------------------------------------------------- university majors (admission data)

    @Transactional(readOnly = true)
    public PageResponse<UniversityMajorResponse> listUniversityMajors(UUID majorId, UUID universityId, Pageable pageable) {
        Pageable sorted = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Order.desc("year"), Sort.Order.asc("id")));
        Page<UniversityMajor> page;
        if (majorId != null && universityId != null) {
            page = universityMajors.findByMajorIdAndUniversityId(majorId, universityId, sorted);
        } else if (majorId != null) {
            page = universityMajors.findByMajorId(majorId, sorted);
        } else if (universityId != null) {
            page = universityMajors.findByUniversityId(universityId, sorted);
        } else {
            page = universityMajors.findAll(sorted);
        }
        return PageResponse.from(page.map(CatalogAdminService::toResponse));
    }

    @Transactional
    public UniversityMajorResponse createUniversityMajor(UniversityMajorRequest request) {
        if (request.universityId() == null || request.majorId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new ErrorDetail("universityId", "universityId and majorId are required when creating")));
        }
        University university = findUniversity(request.universityId());
        Major major = findMajor(request.majorId());
        checkUnique(university.getId(), major.getId(), request.year(), request.combo(), new UUID(0, 0));
        UniversityMajor saved = universityMajors.saveAndFlush(UniversityMajor.create(university, major,
                request.year(), request.combo(), request.cutoffScore(), request.tuitionPerYear()));
        cache.evictAll();
        return toResponse(saved);
    }

    @Transactional
    public UniversityMajorResponse updateUniversityMajor(UUID id, UniversityMajorRequest request) {
        UniversityMajor um = findUniversityMajor(id);
        checkUnique(um.getUniversity().getId(), um.getMajor().getId(), request.year(), request.combo(), id);
        um.update(request.year(), request.combo(), request.cutoffScore(), request.tuitionPerYear());
        universityMajors.flush();
        cache.evictAll();
        return toResponse(um);
    }

    @Transactional
    public void deleteUniversityMajor(UUID id) {
        universityMajors.delete(findUniversityMajor(id));
        universityMajors.flush();
        cache.evictAll();
    }

    // ---------------------------------------------------------------- helpers

    private void changed(UUID majorId) {
        cache.evictAll();
        events.publish(new MajorChangedEvent(majorId));
    }

    private void checkUnique(UUID universityId, UUID majorId, int year, String combo, UUID excludeId) {
        if (universityMajors.existsByUniversityIdAndMajorIdAndYearAndComboAndIdNot(
                universityId, majorId, year, combo, excludeId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE,
                    "This university already has data for this major, year and exam combination");
        }
    }

    private MajorAdminResponse toAdminResponse(Major major) {
        Map<String, BigDecimal> profile = new HashMap<>();
        profiles.findByMajorId(major.getId()).forEach(p -> profile.put(p.getId().dimensionCode(), p.getWeight()));
        return new MajorAdminResponse(major.getId(), major.getCode(), major.getName(), major.getGroupName(),
                major.getShortDescription(), major.getDescription(), List.of(major.getCombos()),
                List.of(major.getTypicalJobs()), major.isActive(), profile);
    }

    private static UniversityMajorResponse toResponse(UniversityMajor um) {
        return new UniversityMajorResponse(um.getId(), um.getUniversity().getId(), um.getUniversity().getName(),
                um.getMajor().getId(), um.getMajor().getName(), um.getYear(), um.getCombo(), um.getCutoffScore(),
                um.getTuitionPerYear(), um.isSample() || um.getUniversity().isSample());
    }

    private static String[] toArray(List<String> values) {
        return values == null ? new String[0] : values.stream().map(String::trim).distinct().toArray(String[]::new);
    }

    private Major findMajor(UUID id) {
        return majors.findById(id).orElseThrow(() -> new NotFoundException(ErrorCode.MAJOR_NOT_FOUND, "Major not found"));
    }

    private University findUniversity(UUID id) {
        return universities.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.UNIVERSITY_NOT_FOUND, "University not found"));
    }

    private UniversityMajor findUniversityMajor(UUID id) {
        return universityMajors.findById(id).orElseThrow(() ->
                new NotFoundException(ErrorCode.UNIVERSITY_MAJOR_NOT_FOUND, "Admission data not found"));
    }
}
