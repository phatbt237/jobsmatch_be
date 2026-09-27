package vn.career.survey.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.career.survey.api.dto.AdminSurveySummary;
import vn.career.survey.domain.Survey;
import vn.career.survey.domain.SurveyStatus;

public interface SurveyRepository extends JpaRepository<Survey, UUID> {

    Optional<Survey> findFirstByStatus(SurveyStatus status);

    @Query("select coalesce(max(s.version), 0) from Survey s")
    int maxVersion();

    @Query(value = "select new vn.career.survey.api.dto.AdminSurveySummary(s.id, s.version, s.title, s.status, "
            + "s.publishedAt, (select count(sec) from SurveySection sec where sec.survey = s), "
            + "(select count(q) from Question q where q.section.survey = s)) "
            + "from Survey s order by s.version desc",
            countQuery = "select count(s) from Survey s")
    Page<AdminSurveySummary> findSummaries(Pageable pageable);
}
