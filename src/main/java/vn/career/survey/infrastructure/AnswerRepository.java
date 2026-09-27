package vn.career.survey.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.survey.domain.Answer;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    @Query("select a from Answer a join fetch a.question where a.attempt.id = :attemptId and a.question.id in :questionIds")
    List<Answer> findByAttemptAndQuestions(@Param("attemptId") UUID attemptId,
                                           @Param("questionIds") Collection<UUID> questionIds);

    @Query("select a from Answer a join fetch a.question q where a.attempt.id = :attemptId "
            + "order by q.section.orderIndex, q.orderIndex")
    List<Answer> findAllOfAttempt(@Param("attemptId") UUID attemptId);

    @Query("select a.question.id from Answer a where a.attempt.id = :attemptId")
    List<UUID> findAnsweredQuestionIds(@Param("attemptId") UUID attemptId);
}
