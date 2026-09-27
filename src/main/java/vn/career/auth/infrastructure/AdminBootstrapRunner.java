package vn.career.auth.infrastructure;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.domain.Role;
import vn.career.auth.domain.User;
import vn.career.auth.domain.UserStatus;

/** Creates the first ADMIN from configuration if it does not exist yet. There is no API to self-register an admin. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminBootstrapProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("app.bootstrap-admin.password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            return;
        }
        users.save(User.create(email, passwordEncoder.encode(properties.password()), properties.fullName(),
                null, Role.ADMIN, UserStatus.ACTIVE));
        log.info("Bootstrap admin account created");
    }
}
