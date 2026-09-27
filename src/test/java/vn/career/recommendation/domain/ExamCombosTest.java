package vn.career.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExamCombosTest {

    private static final Map<String, Double> GPA = Map.of("toan", 8.0, "van", 7.0, "anh", 9.0, "ly", 6.0);

    @Test
    void knownCombinationsHaveThreeSubjects() {
        assertThat(ExamCombos.subjects("A00")).containsExactly("toan", "ly", "hoa");
        assertThat(ExamCombos.subjects("d01")).containsExactly("toan", "van", "anh");
        assertThat(ExamCombos.subjects("XYZ")).isNull();
        assertThat(ExamCombos.subjects(null)).isNull();
    }

    @Test
    void totalAddsTheThreeGrades() {
        assertThat(ExamCombos.total(GPA, "D01").getAsDouble()).isEqualTo(24.0);
        assertThat(ExamCombos.total(GPA, "A01").getAsDouble()).isEqualTo(23.0);
    }

    @Test
    void totalIsEmptyWhenAGradeOrTheCombinationIsMissing() {
        assertThat(ExamCombos.total(GPA, "A00")).isEmpty();   // no "hoa"
        assertThat(ExamCombos.total(GPA, "NOPE")).isEmpty();
        assertThat(ExamCombos.total(null, "D01")).isEmpty();
    }

    @Test
    void bestPicksTheHighestComputableTotal() {
        assertThat(ExamCombos.best(GPA, List.of("A00", "A01", "D01")).getAsDouble()).isEqualTo(24.0);
        assertThat(ExamCombos.best(GPA, List.of("A00", "B00"))).isEmpty();
        assertThat(ExamCombos.best(GPA, List.of())).isEmpty();
    }
}
