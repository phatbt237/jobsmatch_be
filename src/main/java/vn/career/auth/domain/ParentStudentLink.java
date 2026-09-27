package vn.career.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.career.common.domain.BaseEntity;

/**
 * Created by a student as a PENDING invite (parent is null). A parent redeems the code, which fills
 * the parent and flips the status to APPROVED.
 */
@Entity
@Table(name = "parent_student_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentStudentLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private User parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LinkStatus status;

    @Column(name = "invite_code", nullable = false, updatable = false)
    private String inviteCode;

    @Column(name = "invite_expires_at", nullable = false)
    private Instant inviteExpiresAt;

    public static ParentStudentLink invite(User student, String inviteCode, Instant expiresAt) {
        ParentStudentLink link = new ParentStudentLink();
        link.student = student;
        link.inviteCode = inviteCode;
        link.inviteExpiresAt = expiresAt;
        link.status = LinkStatus.PENDING;
        return link;
    }

    public boolean isExpired(Instant now) {
        return !inviteExpiresAt.isAfter(now);
    }

    public void approve(User parent) {
        this.parent = parent;
        this.status = LinkStatus.APPROVED;
    }
}
