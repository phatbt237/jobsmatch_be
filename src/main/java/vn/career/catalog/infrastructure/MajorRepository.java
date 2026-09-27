package vn.career.catalog.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.catalog.domain.Major;

public interface MajorRepository extends JpaRepository<Major, UUID> {

    boolean existsByCode(String code);

    Optional<Major> findByIdAndActiveTrue(UUID id);

    List<Major> findByActiveTrue();

    List<Major> findByIdIn(Collection<UUID> ids);

    Page<Major> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /** Public search over active majors. Every filter is optional (null = ignore). {@code q} is a lower-case LIKE pattern. */
    @Query(value = "select * from majors m where m.is_active = true "
            + "and (cast(:groupName as text) is null or m.group_name = cast(:groupName as text)) "
            + "and (cast(:q as text) is null or lower(m.name) like cast(:q as text)) "
            + "and (cast(:combo as text) is null or cast(:combo as text) = any(m.combos)) "
            + "order by m.name",
            countQuery = "select count(*) from majors m where m.is_active = true "
                    + "and (cast(:groupName as text) is null or m.group_name = cast(:groupName as text)) "
                    + "and (cast(:q as text) is null or lower(m.name) like cast(:q as text)) "
                    + "and (cast(:combo as text) is null or cast(:combo as text) = any(m.combos))",
            nativeQuery = true)
    Page<Major> search(@Param("groupName") String groupName, @Param("q") String q,
                       @Param("combo") String combo, Pageable pageable);
}
