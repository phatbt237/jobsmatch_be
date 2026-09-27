package vn.career.content.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.content.domain.PovPost;
import vn.career.content.domain.PovStatus;

public interface PovPostRepository extends JpaRepository<PovPost, UUID> {

    Page<PovPost> findByMajorIdAndStatus(UUID majorId, PovStatus status, Pageable pageable);

    Optional<PovPost> findByIdAndStatus(UUID id, PovStatus status);

    Page<PovPost> findByMentorId(UUID mentorId, Pageable pageable);

    Page<PovPost> findByStatus(PovStatus status, Pageable pageable);

    List<PovPost> findByMentorId(UUID mentorId);

    List<PovPost> findByStatus(PovStatus status);
}
