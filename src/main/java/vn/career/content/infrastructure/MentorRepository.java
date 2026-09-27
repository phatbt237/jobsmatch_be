package vn.career.content.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.content.domain.Mentor;

public interface MentorRepository extends JpaRepository<Mentor, UUID> {

    Page<Mentor> findByVerified(boolean verified, Pageable pageable);
}
