package vn.career.scoring.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Thresholds of the data-quality checks. */
@ConfigurationProperties("app.scoring")
public record ScoringProperties(
        /** Finishing faster than this many seconds per question raises TOO_FAST. */
        @DefaultValue("3") double minSecondsPerQuestion,
        /** This many wrong attention checks raise ATTENTION_FAILED. */
        @DefaultValue("2") int attentionFailThreshold) {
}
