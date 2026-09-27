package vn.career.content.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.content.domain.ContentStatus;
import vn.career.content.domain.QaThread;

public interface QaThreadRepository extends JpaRepository<QaThread, UUID> {

    Page<QaThread> findByMajorIdAndStatus(UUID majorId, ContentStatus status, Pageable pageable);

    Optional<QaThread> findByIdAndStatus(UUID id, ContentStatus status);

    List<QaThread> findByAuthorId(UUID authorId);
}
