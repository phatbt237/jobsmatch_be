package vn.career.survey.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import vn.career.common.domain.BaseEntity;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;

/** A versioned survey. Only DRAFT surveys can be edited; a PUBLISHED one is immutable, changes need a new version. */
@Entity
@Table(name = "surveys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Survey extends BaseEntity {

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SurveyStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 50)
    private List<SurveySection> sections = new ArrayList<>();

    public static Survey draft(int version, String title) {
        Survey survey = new Survey();
        survey.version = version;
        survey.title = title;
        survey.status = SurveyStatus.DRAFT;
        return survey;
    }

    public boolean isDraft() {
        return status == SurveyStatus.DRAFT;
    }

    /** Guard used before every structural change. */
    public void assertEditable() {
        if (!isDraft()) {
            throw new BusinessException(ErrorCode.SURVEY_NOT_EDITABLE,
                    "Only DRAFT surveys can be edited, create a new version instead");
        }
    }

    public void rename(String newTitle) {
        assertEditable();
        this.title = newTitle;
    }

    public SurveySection addSection(int orderIndex, String code, String title, String description) {
        assertEditable();
        SurveySection section = new SurveySection(this, orderIndex, code, title, description);
        sections.add(section);
        return section;
    }

    public void removeSection(SurveySection section) {
        assertEditable();
        sections.remove(section);
    }

    public void publish(Instant now) {
        this.status = SurveyStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void archive() {
        this.status = SurveyStatus.ARCHIVED;
    }

    public int questionCount() {
        return sections.stream().mapToInt(s -> s.getQuestions().size()).sum();
    }

    /** Deep copy (sections, questions, options) as a new DRAFT with the given version number. */
    public Survey copyAsDraft(int newVersion) {
        Survey copy = draft(newVersion, title);
        for (SurveySection section : sections) {
            SurveySection sectionCopy = copy.addSection(section.getOrderIndex(), section.getCode(),
                    section.getTitle(), section.getDescription());
            for (Question question : section.getQuestions()) {
                sectionCopy.addQuestion(question.toSpec());
            }
        }
        return copy;
    }
}
