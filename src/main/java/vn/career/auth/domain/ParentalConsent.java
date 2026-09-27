package vn.career.auth.domain;

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
import vn.career.common.domain.BaseEntity;

/** Evidence that a parent agreed to their child using the platform. */
@Entity
@Table(name = "parental_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentalConsent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private User parent;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(nullable = false)
    private String method;

    @Column(name = "ip_address")
    private String ipAddress;

    public ParentalConsent(User student, User parent, Instant consentedAt, String method, String ipAddress) {
        this.student = student;
        this.parent = parent;
        this.consentedAt = consentedAt;
        this.method = method;
        this.ipAddress = ipAddress;
    }
}
