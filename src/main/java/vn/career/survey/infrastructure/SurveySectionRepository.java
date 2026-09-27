package vn.career.survey.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.survey.domain.SurveySection;

public interface SurveySectionRepository extends JpaRepository<SurveySection, UUID> {

    boolean existsBySurveyIdAndCodeAndIdNot(UUID surveyId, String code, UUID id);

    boolean existsBySurveyIdAndOrderIndexAndIdNot(UUID surveyId, int orderIndex, UUID id);

    @Query("select coalesce(max(s.orderIndex), 0) from SurveySection s where s.survey.id = :surveyId")
    int maxOrderIndex(@Param("surveyId") UUID surveyId);
}
