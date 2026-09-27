package vn.career.catalog.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.catalog.domain.Major;
import vn.career.catalog.domain.MajorProfile;
import vn.career.catalog.infrastructure.MajorProfileRepository;
import vn.career.catalog.infrastructure.MajorRepository;
import vn.career.catalog.infrastructure.OfferingRow;
import vn.career.catalog.infrastructure.UniversityMajorRepository;

@Service
@RequiredArgsConstructor
class DefaultCatalogQueryApi implements CatalogQueryApi {

    private final MajorRepository majors;
    private final MajorProfileRepository profiles;
    private final UniversityMajorRepository universityMajors;

    @Override
    @Transactional(readOnly = true)
    public boolean majorExists(UUID majorId) {
        return majors.findByIdAndActiveTrue(majorId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeMajorIds() {
        return majors.findByActiveTrue().stream().map(Major::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MajorSummary> summaries(Collection<UUID> majorIds) {
        if (majorIds.isEmpty()) {
            return Map.of();
        }
        return majors.findByIdIn(majorIds).stream().collect(Collectors.toMap(Major::getId,
                m -> new MajorSummary(m.getId(), m.getCode(), m.getName(), m.getGroupName())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MajorMatchData> loadActiveMajorsForMatching() {
        Map<UUID, Map<String, BigDecimal>> profileByMajor = new HashMap<>();
        for (MajorProfile p : profiles.findAll()) {
            profileByMajor.computeIfAbsent(p.getId().majorId(), k -> new HashMap<>())
                    .put(p.getId().dimensionCode(), p.getWeight());
        }
        Map<UUID, List<Offering>> offeringsByMajor = new HashMap<>();
        for (OfferingRow row : universityMajors.findLatestYearOfferings()) {
            offeringsByMajor.computeIfAbsent(row.majorId(), k -> new ArrayList<>()).add(new Offering(
                    row.region().name(), row.tuitionPerYear(), row.cutoffScore(), row.year(), row.combo()));
        }
        return majors.findByActiveTrue().stream()
                .map(m -> new MajorMatchData(m.getId(), m.getCode(), m.getName(), m.isActive(),
                        List.of(m.getCombos()), profileByMajor.getOrDefault(m.getId(), Map.of()),
                        offeringsByMajor.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MajorDocument> majorDocument(UUID majorId) {
        return majors.findByIdAndActiveTrue(majorId).map(m -> {
            StringBuilder text = new StringBuilder();
            text.append("Ngành ").append(m.getName()).append(" (nhóm ").append(m.getGroupName()).append(").\n");
            if (m.getShortDescription() != null) {
                text.append(m.getShortDescription()).append('\n');
            }
            if (m.getDescription() != null) {
                text.append(m.getDescription()).append('\n');
            }
            if (m.getTypicalJobs().length > 0) {
                text.append("Công việc thường gặp: ").append(String.join(", ", m.getTypicalJobs())).append(".\n");
            }
            if (m.getCombos().length > 0) {
                text.append("Tổ hợp xét tuyển: ").append(String.join(", ", m.getCombos())).append('.');
            }
            return new MajorDocument(m.getId(), m.getName(), text.toString());
        });
    }
}
