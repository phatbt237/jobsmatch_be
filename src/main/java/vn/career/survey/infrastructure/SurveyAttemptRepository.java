package vn.career.survey.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.survey.domain.AttemptStatus;
import vn.career.survey.domain.SurveyAttempt;

public interface SurveyAttemptRepository extends JpaRepository<SurveyAttempt, UUID> {

    @EntityGraph(attributePaths = "survey")
    Optional<SurveyAttempt> findFirstByUserIdAndStatus(UUID userId, AttemptStatus status);

    @EntityGraph(attributePaths = "survey")
    Page<SurveyAttempt> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    /** Row lock: serialises concurrent autosaves of the same attempt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SurveyAttempt a join fetch a.survey where a.id = :id")
    Optional<SurveyAttempt> findForUpdate(@Param("id") UUID id);

    @Query("select a from SurveyAttempt a join fetch a.survey where a.id = :id")
    Optional<SurveyAttempt> findWithSurveyById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "survey")
    List<SurveyAttempt> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    @Query("select a.id from SurveyAttempt a where a.userId = :userId order by a.startedAt desc")
    List<UUID> findIdsByUserId(@Param("userId") UUID userId);

    /** Bulk delete; the database cascades to answers, scores, constraints, recommendations and summaries. */
    @Modifying
    @Query("delete from SurveyAttempt a where a.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    /** Transaction-scoped advisory lock, so two concurrent "start attempt" calls of one user cannot both create one. */
    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)", nativeQuery = true)
    String lockByKey(@Param("key") String key);
}
