package vn.career.recommendation.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * University entrance exam combinations and their three subjects. Grades use the subject codes students enter in
 * section D: toan, van, anh, ly, hoa, sinh, su, dia.
 */
public final class ExamCombos {

    private static final Map<String, List<String>> SUBJECTS = Map.ofEntries(
            Map.entry("A00", List.of("toan", "ly", "hoa")),
            Map.entry("A01", List.of("toan", "ly", "anh")),
            Map.entry("A02", List.of("toan", "ly", "sinh")),
            Map.entry("B00", List.of("toan", "hoa", "sinh")),
            Map.entry("C00", List.of("van", "su", "dia")),
            Map.entry("C01", List.of("van", "toan", "ly")),
            Map.entry("C03", List.of("van", "toan", "su")),
            Map.entry("C04", List.of("van", "toan", "dia")),
            Map.entry("D01", List.of("toan", "van", "anh")),
            Map.entry("D07", List.of("toan", "hoa", "anh")),
            Map.entry("D08", List.of("toan", "sinh", "anh")),
            Map.entry("D09", List.of("toan", "su", "anh")),
            Map.entry("D10", List.of("toan", "dia", "anh")),
            Map.entry("D14", List.of("van", "su", "anh")),
            Map.entry("D15", List.of("van", "dia", "anh")));

    private ExamCombos() {
    }

    /** Subjects of a combination, or null when the combination is not known. */
    public static List<String> subjects(String combo) {
        return combo == null ? null : SUBJECTS.get(combo.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** Sum of the three grades of one combination, empty if it is unknown or a grade is missing. */
    public static OptionalDouble total(Map<String, Double> gpa, String combo) {
        List<String> subjects = subjects(combo);
        if (subjects == null || gpa == null) {
            return OptionalDouble.empty();
        }
        double sum = 0;
        for (String subject : subjects) {
            Double grade = gpa.get(subject);
            if (grade == null) {
                return OptionalDouble.empty();
            }
            sum += grade;
        }
        return OptionalDouble.of(sum);
    }

    /** Best total over the given combinations that can be computed. */
    public static OptionalDouble best(Map<String, Double> gpa, Collection<String> combos) {
        return combos.stream()
                .map(combo -> total(gpa, combo))
                .filter(OptionalDouble::isPresent)
                .mapToDouble(OptionalDouble::getAsDouble)
                .max();
    }
}
