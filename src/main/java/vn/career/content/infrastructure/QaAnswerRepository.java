package vn.career.content.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.content.domain.ContentStatus;
import vn.career.content.domain.QaAnswer;

public interface QaAnswerRepository extends JpaRepository<QaAnswer, UUID> {

    Page<QaAnswer> findByThreadIdAndStatus(UUID threadId, ContentStatus status, Pageable pageable);

    List<QaAnswer> findByAuthorId(UUID authorId);

    /** Rows of (thread id, number of visible answers). */
    @Query("select a.threadId, count(a) from QaAnswer a where a.threadId in :threadIds and a.status = :status group by a.threadId")
    List<Object[]> countByThread(@Param("threadIds") Collection<UUID> threadIds, @Param("status") ContentStatus status);
}
