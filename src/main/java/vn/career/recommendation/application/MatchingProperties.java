package vn.career.recommendation.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Tunable numbers of the matching algorithm. The three weights must add up to 1 or the app refuses to start. */
@Validated
@ConfigurationProperties("app.matching")
public record MatchingProperties(
        @DefaultValue("0.5") @DecimalMin("0") @DecimalMax("1") double interestWeight,
        @DefaultValue("0.25") @DecimalMin("0") @DecimalMax("1") double aptitudeWeight,
        @DefaultValue("0.25") @DecimalMin("0") @DecimalMax("1") double valuesWeight,
        @DefaultValue("0.10") @DecimalMin("0") @DecimalMax("1") double budgetPenalty,
        @DefaultValue("0.10") @DecimalMin("0") @DecimalMax("1") double scorePenalty,
        /** Penalty applies when the expected total is more than this many points below the lowest cutoff. */
        @DefaultValue("3") @DecimalMin("0") double scoreGapThreshold,
        @DefaultValue("5") @Min(1) @Max(20) int topN) {

    @AssertTrue(message = "app.matching interest, aptitude and values weights must add up to 1")
    public boolean isWeightsSumToOne() {
        return Math.abs(interestWeight + aptitudeWeight + valuesWeight - 1.0) < 1e-6;
    }
}
