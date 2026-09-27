package vn.career.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Profile of a working professional. The key is the user id, so this does not extend BaseEntity. */
@Entity
@Table(name = "mentors")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mentor {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    private String company;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "years_experience", nullable = false)
    private int yearsExperience;

    @Column(name = "major_id")
    private UUID majorId;

    private String bio;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    public static Mentor apply(UUID userId, String company, String jobTitle, int yearsExperience, UUID majorId,
                               String bio, String linkedinUrl) {
        Mentor mentor = new Mentor();
        mentor.userId = userId;
        mentor.company = company;
        mentor.jobTitle = jobTitle;
        mentor.yearsExperience = yearsExperience;
        mentor.majorId = majorId;
        mentor.bio = bio;
        mentor.linkedinUrl = linkedinUrl;
        mentor.verified = false;
        return mentor;
    }

    public void setVerified(boolean verified, UUID adminId, Instant now) {
        this.verified = verified;
        this.verifiedBy = verified ? adminId : null;
        this.verifiedAt = verified ? now : null;
    }
}
