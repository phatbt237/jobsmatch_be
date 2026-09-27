package vn.career.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Students younger than {@code minAge} need a parent's consent before using AI chat and community Q&A. */
@ConfigurationProperties("app.consent")
public record ConsentProperties(@DefaultValue("16") int minAge) {
}
