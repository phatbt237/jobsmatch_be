package vn.career.survey.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.career.common.domain.BaseEntity;

@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private SurveySection section;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false)
    private String content;

    /** Null for attention checks and purely informational questions. */
    @Column(name = "dimension_code")
    private String dimensionCode;

    @Column(nullable = false)
    private BigDecimal weight;

    @Column(name = "reverse_scored", nullable = false)
    private boolean reverseScored;

    @Column(name = "is_attention_check", nullable = false)
    private boolean attentionCheck;

    /** Only for attention checks: the answer a careful respondent gives. Never sent to students. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_value")
    private JsonNode expectedValue;

    @Column(nullable = false)
    private boolean required;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 100)
    private List<QuestionOption> options = new ArrayList<>();

    static Question create(SurveySection section, QuestionSpec spec) {
        Question question = new Question();
        question.section = section;
        question.apply(spec);
        return question;
    }

    /** Overwrites all editable fields and replaces the options. Option order follows the list order. */
    public void apply(QuestionSpec spec) {
        section.getSurvey().assertEditable();
        this.orderIndex = spec.orderIndex();
        this.type = spec.type();
        this.content = spec.content();
        this.dimensionCode = spec.dimensionCode();
        this.weight = spec.weight();
        this.reverseScored = spec.reverseScored();
        this.attentionCheck = spec.attentionCheck();
        this.expectedValue = spec.expectedValue();
        this.required = spec.required();
        options.clear();
        List<QuestionSpec.OptionSpec> optionSpecs = spec.options() == null ? List.of() : spec.options();
        for (int i = 0; i < optionSpecs.size(); i++) {
            QuestionSpec.OptionSpec option = optionSpecs.get(i);
            options.add(new QuestionOption(this, i + 1, option.label(), option.value(), option.correct()));
        }
    }

    public QuestionSpec toSpec() {
        List<QuestionSpec.OptionSpec> optionSpecs = options.stream()
                .map(o -> new QuestionSpec.OptionSpec(o.getLabel(), o.getValue(), o.isCorrect()))
                .toList();
        return new QuestionSpec(type, content, dimensionCode, weight, reverseScored, attentionCheck,
                expectedValue, required, orderIndex, optionSpecs);
    }
}
