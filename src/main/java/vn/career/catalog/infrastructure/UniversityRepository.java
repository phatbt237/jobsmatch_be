package vn.career.catalog.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.catalog.domain.Region;
import vn.career.catalog.domain.University;

public interface UniversityRepository extends JpaRepository<University, UUID> {

    boolean existsByCode(String code);

    List<University> findAllByOrderByName();

    List<University> findByRegionOrderByName(Region region);

    Page<University> findAllByOrderByName(Pageable pageable);
}
