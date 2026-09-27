package vn.career.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.career.common.domain.BaseEntity;

@Entity
@Table(name = "question_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private String label;

    /** Numeric score of this option (SINGLE_CHOICE). Never sent to students. */
    @Column
    private BigDecimal value;

    /** Marks the right answer of a MINI_TEST. Never sent to students. */
    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    QuestionOption(Question question, int orderIndex, String label, BigDecimal value, boolean correct) {
        this.question = question;
        this.orderIndex = orderIndex;
        this.label = label;
        this.value = value;
        this.correct = correct;
    }
}
