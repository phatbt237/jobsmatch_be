package vn.career.survey.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.career.common.domain.BaseEntity;

/** A raw answer. The JSON shape depends on the question type, see {@link QuestionType}. */
@Entity
@Table(name = "answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    private SurveyAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private Question question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode value;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    public static Answer create(SurveyAttempt attempt, Question question, JsonNode value, Instant now) {
        Answer answer = new Answer();
        answer.attempt = attempt;
        answer.question = question;
        answer.value = value;
        answer.answeredAt = now;
        return answer;
    }

    public void update(JsonNode newValue, Instant now) {
        this.value = newValue;
        this.answeredAt = now;
    }
}
