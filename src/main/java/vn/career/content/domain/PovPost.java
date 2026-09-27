package vn.career.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.career.common.domain.BaseEntity;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;

/**
 * A mentor's first-person post about a career. Lifecycle: DRAFT -> PENDING_REVIEW -> PUBLISHED or REJECTED;
 * a REJECTED post can be edited and sent for review again. Only DRAFT and REJECTED posts can be edited.
 */
@Entity
@Table(name = "pov_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PovPost extends BaseEntity {

    @Column(name = "mentor_id", nullable = false, updatable = false)
    private UUID mentorId;

    @Column(name = "major_id", nullable = false)
    private UUID majorId;

    @Column(nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> sections = new LinkedHashMap<>();

    @Column(name = "video_url")
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PovStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    public static PovPost draft(UUID mentorId, UUID majorId, String title, Map<String, String> sections, String videoUrl) {
        PovPost post = new PovPost();
        post.mentorId = mentorId;
        post.majorId = majorId;
        post.title = title;
        post.sections = new LinkedHashMap<>(sections);
        post.videoUrl = videoUrl;
        post.status = PovStatus.DRAFT;
        return post;
    }

    public boolean isEditable() {
        return status == PovStatus.DRAFT || status == PovStatus.REJECTED;
    }

    public void edit(UUID majorId, String title, Map<String, String> sections, String videoUrl) {
        if (!isEditable()) {
            throw new BusinessException(ErrorCode.POV_NOT_EDITABLE,
                    "Only DRAFT or REJECTED posts can be edited, this one is " + status);
        }
        this.majorId = majorId;
        this.title = title;
        this.sections = new LinkedHashMap<>(sections);
        this.videoUrl = videoUrl;
    }

    public void submitForReview() {
        if (!isEditable()) {
            throw new BusinessException(ErrorCode.POV_INVALID_STATE, "Only DRAFT or REJECTED posts can be submitted, this one is " + status);
        }
        this.status = PovStatus.PENDING_REVIEW;
        this.rejectReason = null;
    }

    public void publish(Instant now) {
        requirePending();
        this.status = PovStatus.PUBLISHED;
        this.publishedAt = now;
        this.rejectReason = null;
    }

    public void reject(String reason) {
        requirePending();
        this.status = PovStatus.REJECTED;
        this.rejectReason = reason;
    }

    private void requirePending() {
        if (status != PovStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.POV_INVALID_STATE, "Only posts waiting for review can be reviewed, this one is " + status);
        }
    }
}
