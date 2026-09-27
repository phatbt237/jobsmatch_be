package vn.career.survey.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.survey.domain.Question;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    boolean existsBySectionIdAndOrderIndexAndIdNot(UUID sectionId, int orderIndex, UUID id);

    @Query("select coalesce(max(q.orderIndex), 0) from Question q where q.section.id = :sectionId")
    int maxOrderIndex(@Param("sectionId") UUID sectionId);

    /** Loads the given questions (with options) but only those that belong to the given survey. */
    @Query("select distinct q from Question q left join fetch q.options "
            + "where q.section.survey.id = :surveyId and q.id in :ids")
    List<Question> findInSurvey(@Param("surveyId") UUID surveyId, @Param("ids") Collection<UUID> ids);
}
