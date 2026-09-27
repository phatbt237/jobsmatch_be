package vn.career.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UserConsentRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Test
    void studentUnderMinAgeNeedsConsent() {
        assertThat(User.requiresParentalConsent(Role.STUDENT, LocalDate.of(2010, 6, 16), 16, TODAY)).isTrue();
    }

    @Test
    void studentTurningMinAgeTodayDoesNotNeedConsent() {
        assertThat(User.requiresParentalConsent(Role.STUDENT, LocalDate.of(2010, 6, 15), 16, TODAY)).isFalse();
    }

    @Test
    void thresholdFollowsConfiguration() {
        LocalDate dob = LocalDate.of(2009, 1, 1); // 17 years old
        assertThat(User.requiresParentalConsent(Role.STUDENT, dob, 16, TODAY)).isFalse();
        assertThat(User.requiresParentalConsent(Role.STUDENT, dob, 18, TODAY)).isTrue();
    }

    @Test
    void parentNeverNeedsConsent() {
        assertThat(User.requiresParentalConsent(Role.PARENT, LocalDate.of(2015, 1, 1), 16, TODAY)).isFalse();
    }

    @Test
    void grantingConsentOnlyChangesPendingAccounts() {
        User pending = User.create("a@x.com", "h", "A", null, Role.STUDENT, UserStatus.PENDING_PARENT_CONSENT);
        User locked = User.create("b@x.com", "h", "B", null, Role.STUDENT, UserStatus.LOCKED);

        pending.grantParentalConsent();
        locked.grantParentalConsent();

        assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(locked.getStatus()).isEqualTo(UserStatus.LOCKED);
    }
}
