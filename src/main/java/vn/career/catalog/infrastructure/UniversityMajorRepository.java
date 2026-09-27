package vn.career.catalog.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.catalog.domain.UniversityMajor;

public interface UniversityMajorRepository extends JpaRepository<UniversityMajor, UUID> {

    boolean existsByUniversityIdAndMajorIdAndYearAndComboAndIdNot(UUID universityId, UUID majorId, int year,
                                                                   String combo, UUID id);

    @Query("select distinct um.year from UniversityMajor um where um.major.id = :majorId order by um.year desc")
    List<Integer> findYears(@Param("majorId") UUID majorId);

    @Query("select um from UniversityMajor um join fetch um.university "
            + "where um.major.id = :majorId and um.year in :years order by um.university.name, um.year desc, um.combo")
    List<UniversityMajor> findForMajor(@Param("majorId") UUID majorId, @Param("years") Collection<Integer> years);

    /** For every major, the rows of the most recent year that has data for it. */
    @Query("select new vn.career.catalog.infrastructure.OfferingRow(um.major.id, u.region, um.tuitionPerYear, "
            + "um.cutoffScore, um.year, um.combo) from UniversityMajor um join um.university u "
            + "where um.year = (select max(x.year) from UniversityMajor x where x.major.id = um.major.id)")
    List<OfferingRow> findLatestYearOfferings();

    // Admin listing. Separate methods instead of "(:x is null or ...)" because PostgreSQL cannot infer the type
    // of an untyped null UUID parameter.
    @Override
    @EntityGraph(attributePaths = {"university", "major"})
    Page<UniversityMajor> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"university", "major"})
    Page<UniversityMajor> findByMajorId(UUID majorId, Pageable pageable);

    @EntityGraph(attributePaths = {"university", "major"})
    Page<UniversityMajor> findByUniversityId(UUID universityId, Pageable pageable);

    @EntityGraph(attributePaths = {"university", "major"})
    Page<UniversityMajor> findByMajorIdAndUniversityId(UUID majorId, UUID universityId, Pageable pageable);
}
