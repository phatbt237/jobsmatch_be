package vn.career.survey.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.survey.domain.StudentConstraints;

public interface StudentConstraintsRepository extends JpaRepository<StudentConstraints, UUID> {
}
