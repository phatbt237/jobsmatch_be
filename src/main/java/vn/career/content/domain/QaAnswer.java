package vn.career.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.career.common.domain.BaseEntity;

@Entity
@Table(name = "qa_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QaAnswer extends BaseEntity {

    @Column(name = "thread_id", nullable = false, updatable = false)
    private UUID threadId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String content;

    /** True when a verified mentor wrote it, so the UI can highlight it. */
    @Column(name = "is_mentor_answer", nullable = false)
    private boolean mentorAnswer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status;

    public static QaAnswer reply(UUID threadId, UUID authorId, String content, boolean mentorAnswer) {
        QaAnswer answer = new QaAnswer();
        answer.threadId = threadId;
        answer.authorId = authorId;
        answer.content = content;
        answer.mentorAnswer = mentorAnswer;
        answer.status = ContentStatus.VISIBLE;
        return answer;
    }

    public void moderate(ContentStatus newStatus) {
        this.status = newStatus;
    }
}
