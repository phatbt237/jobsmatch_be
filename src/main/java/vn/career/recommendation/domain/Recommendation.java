package vn.career.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.career.common.domain.BaseEntity;

/** One ranked major for a scored attempt. Attempt and major are referenced by id only (they live in other modules). */
@Entity
@Table(name = "recommendations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation extends BaseEntity {

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "major_id", nullable = false, updatable = false)
    private UUID majorId;

    @Column(name = "rank", nullable = false)
    private int rankPosition;

    @Column(nullable = false)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", nullable = false)
    private Map<String, Object> scoreBreakdown;

    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "explanation_status", nullable = false)
    private ExplanationStatus explanationStatus;

    @Column(name = "algo_version", nullable = false)
    private String algoVersion;

    @Column(name = "llm_model")
    private String llmModel;

    public static Recommendation create(UUID attemptId, MajorMatcher.Result result, String algoVersion) {
        Recommendation r = new Recommendation();
        r.attemptId = attemptId;
        r.majorId = result.majorId();
        r.rankPosition = result.rank();
        r.score = BigDecimal.valueOf(result.score()).setScale(4, RoundingMode.HALF_UP);
        r.scoreBreakdown = result.breakdown();
        r.explanationStatus = ExplanationStatus.PENDING;
        r.algoVersion = algoVersion;
        return r;
    }

    public void explain(String text, String model) {
        this.explanation = text;
        this.llmModel = model;
        this.explanationStatus = ExplanationStatus.DONE;
    }

    public void markFailed() {
        this.explanationStatus = ExplanationStatus.FAILED;
    }
}
