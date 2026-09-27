package vn.career.auth.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Temporary lock after {@code maxAttempts} failed logins for one email within {@code lockout}. */
@ConfigurationProperties("app.login-limit")
public record LoginLimitProperties(@DefaultValue("5") int maxAttempts, @DefaultValue("15m") Duration lockout) {
}
