package vn.career.recommendation.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.recommendation.domain.Recommendation;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    List<Recommendation> findByAttemptIdOrderByRankPosition(UUID attemptId);

    void deleteByAttemptId(UUID attemptId);
}
