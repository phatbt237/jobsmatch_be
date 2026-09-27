package vn.career.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Optional first admin account, created at startup when both email and password are set. */
@ConfigurationProperties("app.bootstrap-admin")
public record AdminBootstrapProperties(
        String email,
        String password,
        @DefaultValue("Administrator") String fullName) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
