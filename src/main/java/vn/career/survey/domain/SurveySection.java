package vn.career.survey.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import vn.career.common.domain.BaseEntity;

@Entity
@Table(name = "survey_sections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveySection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    private String description;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 50)
    private List<Question> questions = new ArrayList<>();

    SurveySection(Survey survey, int orderIndex, String code, String title, String description) {
        this.survey = survey;
        this.orderIndex = orderIndex;
        this.code = code;
        this.title = title;
        this.description = description;
    }

    public void update(int orderIndex, String code, String title, String description) {
        survey.assertEditable();
        this.orderIndex = orderIndex;
        this.code = code;
        this.title = title;
        this.description = description;
    }

    public Question addQuestion(QuestionSpec spec) {
        survey.assertEditable();
        Question question = Question.create(this, spec);
        questions.add(question);
        return question;
    }

    public void removeQuestion(Question question) {
        survey.assertEditable();
        questions.remove(question);
    }
}
