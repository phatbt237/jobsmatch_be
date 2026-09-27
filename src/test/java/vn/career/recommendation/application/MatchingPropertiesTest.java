package vn.career.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class MatchingPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultWeightsAddUpToOne() {
        assertThat(validator.validate(new MatchingProperties(0.5, 0.25, 0.25, 0.1, 0.1, 3, 5))).isEmpty();
    }

    @Test
    void weightsThatDoNotAddUpToOneAreRejected() {
        assertThat(validator.validate(new MatchingProperties(0.5, 0.3, 0.3, 0.1, 0.1, 3, 5))).isNotEmpty();
        assertThat(validator.validate(new MatchingProperties(0.4, 0.2, 0.2, 0.1, 0.1, 3, 5))).isNotEmpty();
    }

    @Test
    void tinyRoundingDifferencesAreAccepted() {
        assertThat(validator.validate(new MatchingProperties(0.3333333, 0.3333333, 0.3333334, 0.1, 0.1, 3, 5))).isEmpty();
    }

    @Test
    void outOfRangeValuesAreRejected() {
        assertThat(validator.validate(new MatchingProperties(0.5, 0.25, 0.25, 1.5, 0.1, 3, 5))).isNotEmpty();
        assertThat(validator.validate(new MatchingProperties(0.5, 0.25, 0.25, 0.1, 0.1, 3, 0))).isNotEmpty();
    }
}
