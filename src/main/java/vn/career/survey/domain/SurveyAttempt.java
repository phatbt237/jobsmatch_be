package vn.career.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.career.common.domain.BaseEntity;

/**
 * One student's run through a survey. It stays bound to the survey version it started with,
 * even if a newer version is published later. The user is referenced by id only (no dependency on the auth module).
 */
@Entity
@Table(name = "survey_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyAttempt extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false, updatable = false)
    private Survey survey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_flags")
    private Map<String, Object> qualityFlags;

    public static SurveyAttempt start(UUID userId, Survey survey, Instant now) {
        SurveyAttempt attempt = new SurveyAttempt();
        attempt.userId = userId;
        attempt.survey = survey;
        attempt.status = AttemptStatus.IN_PROGRESS;
        attempt.startedAt = now;
        return attempt;
    }

    /** Final state after scoring: stores when it was submitted and the data-quality flags. */
    public void markScored(Instant submittedAt, Map<String, Object> flags) {
        this.status = AttemptStatus.SCORED;
        this.submittedAt = submittedAt;
        this.qualityFlags = flags;
    }

    public boolean isInProgress() {
        return status == AttemptStatus.IN_PROGRESS;
    }

    public boolean isOwnedBy(UUID id) {
        return userId.equals(id);
    }
}
