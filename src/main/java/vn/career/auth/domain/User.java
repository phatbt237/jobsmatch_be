package vn.career.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.Period;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.career.common.domain.BaseEntity;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    public static User create(String email, String passwordHash, String fullName, LocalDate dateOfBirth,
                              Role role, UserStatus status) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.dateOfBirth = dateOfBirth;
        user.role = role;
        user.status = status;
        return user;
    }

    /** True for a student younger than {@code minAge} on {@code today}. */
    public static boolean requiresParentalConsent(Role role, LocalDate dateOfBirth, int minAge, LocalDate today) {
        return role == Role.STUDENT
                && dateOfBirth != null
                && Period.between(dateOfBirth, today).getYears() < minAge;
    }

    /**
     * Irreversibly strips personal data after an account deletion. The row stays (other tables and audit entries refer
     * to its id) but it holds no name, birth date or real email and can never log in.
     */
    public void anonymize(String placeholderEmail, String unusablePasswordHash) {
        this.email = placeholderEmail;
        this.passwordHash = unusablePasswordHash;
        this.fullName = null;
        this.dateOfBirth = null;
        this.status = UserStatus.LOCKED;
    }

    public static final String DELETED_EMAIL_SUFFIX = "@deleted.invalid";

    public boolean isAnonymized() {
        return email != null && email.endsWith(DELETED_EMAIL_SUFFIX);
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public boolean isLocked() {
        return status == UserStatus.LOCKED;
    }

    /** Moves a student waiting for consent to ACTIVE. Other statuses are left untouched. */
    public void grantParentalConsent() {
        if (status == UserStatus.PENDING_PARENT_CONSENT) {
            status = UserStatus.ACTIVE;
        }
    }
}
