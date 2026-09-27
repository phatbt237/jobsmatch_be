package vn.career.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.parent-invite")
public record ParentInviteProperties(@DefaultValue("7d") Duration ttl) {
}
