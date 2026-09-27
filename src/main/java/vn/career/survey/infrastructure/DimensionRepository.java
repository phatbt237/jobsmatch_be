package vn.career.survey.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.survey.domain.Dimension;

public interface DimensionRepository extends JpaRepository<Dimension, String> {

    List<Dimension> findAllByOrderByGroupAscCodeAsc();
}
