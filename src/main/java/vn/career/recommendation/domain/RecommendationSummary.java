package vn.career.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The overall paragraph of an AI explanation (one per attempt). */
@Entity
@Table(name = "recommendation_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationSummary {

    @Id
    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(nullable = false)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RecommendationSummary(UUID attemptId, String summary, Instant createdAt) {
        this.attemptId = attemptId;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public void rewrite(String newSummary) {
        this.summary = newSummary;
    }
}
