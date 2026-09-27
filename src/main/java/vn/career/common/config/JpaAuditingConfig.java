package vn.career.common.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import vn.career.common.security.SecurityUtils;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    /** created_by / updated_by come from the current JWT principal, empty for anonymous requests such as register. */
    @Bean
    AuditorAware<UUID> auditorProvider() {
        return SecurityUtils::currentUserId;
    }

    /** Application clock. Vietnam time zone so age calculations use the student's local date. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
