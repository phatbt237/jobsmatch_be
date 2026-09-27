package vn.career.recommendation.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.career.recommendation.domain.RecommendationSummary;

public interface RecommendationSummaryRepository extends JpaRepository<RecommendationSummary, UUID> {
}
