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

/** A question about a major asked by a student (or anyone allowed to take part). */
@Entity
@Table(name = "qa_threads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QaThread extends BaseEntity {

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "major_id", nullable = false, updatable = false)
    private UUID majorId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status;

    public static QaThread ask(UUID authorId, UUID majorId, String title, String content) {
        QaThread thread = new QaThread();
        thread.authorId = authorId;
        thread.majorId = majorId;
        thread.title = title;
        thread.content = content;
        thread.status = ContentStatus.VISIBLE;
        return thread;
    }

    public void moderate(ContentStatus newStatus) {
        this.status = newStatus;
    }
}
