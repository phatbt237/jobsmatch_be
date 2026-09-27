package vn.career.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.career.recommendation.domain.MajorMatcher.Candidate;
import vn.career.recommendation.domain.MajorMatcher.Offering;
import vn.career.recommendation.domain.MajorMatcher.Penalty;
import vn.career.recommendation.domain.MajorMatcher.Result;
import vn.career.recommendation.domain.MajorMatcher.Student;

class MajorMatcherTest {

    private static final List<String> INTEREST = List.of("A", "C", "E", "I", "R", "S");
    private static final List<String> APTITUDE = List.of("LOGIC", "NUMERIC", "VERBAL");
    private static final List<String> VALUES = List.of("VAL_HELPING", "VAL_INCOME");
    private static final MajorMatcher.Layout LAYOUT = new MajorMatcher.Layout(INTEREST, APTITUDE, VALUES);

    private static MajorMatcher.Config config(int topN) {
        return new MajorMatcher.Config(0.5, 0.25, 0.25, 0.10, 0.10, 3.0, topN, LAYOUT);
    }

    private final MajorMatcher matcher = new MajorMatcher(config(5));

    // ---------- test data ----------

    private static Candidate major(String code, Map<String, Double> profile) {
        return new Candidate(UUID.nameUUIDFromBytes(code.getBytes()), code, true, List.of("A00", "D01"), profile, List.of());
    }

    private static Candidate withCombos(Candidate c, List<String> combos) {
        return new Candidate(c.id(), c.code(), c.active(), combos, c.profile(), c.offerings());
    }

    private static Candidate withOfferings(Candidate c, Offering... offerings) {
        return new Candidate(c.id(), c.code(), c.active(), c.combos(), c.profile(), List.of(offerings));
    }

    private static Student student(Map<String, Double> scores) {
        return new Student(scores, List.of(), Map.of(), null, List.of());
    }

    private static Student withBudget(Student s, Long budget, List<String> regions) {
        return new Student(s.scores(), s.combos(), s.gpa(), budget, regions);
    }

    private static Student withGpa(Student s, List<String> combos, Map<String, Double> gpa) {
        return new Student(s.scores(), combos, gpa, s.budgetPerYear(), s.regions());
    }

    private static Map<String, Double> map(Object... keyValues) {
        Map<String, Double> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], ((Number) keyValues[i + 1]).doubleValue());
        }
        return map;
    }

    private static final Candidate ACCOUNTING = major("ACCOUNTING",
            map("C", 0.9, "I", 0.4, "E", 0.3, "LOGIC", 0.6, "NUMERIC", 1.0, "VERBAL", 0.4));
    private static final Candidate DATA_SCIENCE = major("DATA_SCIENCE",
            map("I", 0.9, "C", 0.6, "R", 0.3, "LOGIC", 1.0, "NUMERIC", 1.0, "VERBAL", 0.4, "VAL_INCOME", 0.8));
    private static final Candidate GRAPHIC_DESIGN = major("GRAPHIC_DESIGN",
            map("A", 1.0, "R", 0.3, "E", 0.3, "I", 0.3, "LOGIC", 0.3, "NUMERIC", 0.2, "VERBAL", 0.4));
    private static final Candidate NURSING = major("NURSING",
            map("S", 0.9, "I", 0.5, "R", 0.5, "C", 0.5, "VERBAL", 0.5, "VAL_HELPING", 1.0));

    private static final Student ANALYTICAL = student(map("I", 0.95, "C", 0.9, "R", 0.4, "A", 0.1, "E", 0.2, "S", 0.2,
            "LOGIC", 0.9, "NUMERIC", 0.9, "VERBAL", 0.4, "VAL_INCOME", 0.7, "VAL_HELPING", 0.2));

    // ---------- ranking ----------

    @Test
    void studentWithHighInvestigativeAndConventionalRanksAccountingAndDataScienceAboveGraphicDesign() {
        List<Result> results = matcher.match(ANALYTICAL, List.of(GRAPHIC_DESIGN, ACCOUNTING, NURSING, DATA_SCIENCE));

        List<String> order = results.stream().map(r -> code(r, List.of(GRAPHIC_DESIGN, ACCOUNTING, NURSING, DATA_SCIENCE))).toList();
        assertThat(order.indexOf("ACCOUNTING")).isLessThan(order.indexOf("GRAPHIC_DESIGN"));
        assertThat(order.indexOf("DATA_SCIENCE")).isLessThan(order.indexOf("GRAPHIC_DESIGN"));
        assertThat(order.get(order.size() - 1)).isIn("GRAPHIC_DESIGN", "NURSING");
    }

    @Test
    void creativeStudentGetsGraphicDesignOnTop() {
        Student creative = student(map("A", 0.95, "E", 0.3, "I", 0.2, "C", 0.1, "LOGIC", 0.3, "VERBAL", 0.5));

        List<Candidate> all = List.of(ACCOUNTING, DATA_SCIENCE, GRAPHIC_DESIGN, NURSING);
        assertThat(code(matcher.match(creative, all).get(0), all)).isEqualTo("GRAPHIC_DESIGN");
    }

    private static String code(Result result, List<Candidate> candidates) {
        return candidates.stream().filter(c -> c.id().equals(result.majorId())).findFirst().orElseThrow().code();
    }

    @Test
    void resultsAreRankedFromOneAndScoresNeverIncrease() {
        List<Result> results = matcher.match(ANALYTICAL, List.of(ACCOUNTING, DATA_SCIENCE, GRAPHIC_DESIGN, NURSING));

        assertThat(results).extracting(Result::rank).containsExactly(1, 2, 3, 4);
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i).score()).isLessThanOrEqualTo(results.get(i - 1).score());
        }
    }

    @Test
    void onlyTheTopNAreReturned() {
        MajorMatcher top2 = new MajorMatcher(config(2));

        List<Result> results = top2.match(ANALYTICAL, List.of(ACCOUNTING, DATA_SCIENCE, GRAPHIC_DESIGN, NURSING));

        assertThat(results).hasSize(2);
    }

    @Test
    void baseScoreIsTheWeightedSumOfTheThreeCosines() {
        Result r = matcher.match(ANALYTICAL, List.of(DATA_SCIENCE)).get(0);

        assertThat(r.base()).isCloseTo(0.5 * r.interest() + 0.25 * r.aptitude() + 0.25 * r.values(), within(1e-9));
        assertThat(r.score()).isCloseTo(r.base(), within(1e-9));   // no penalties here
        assertThat(r.interest()).isBetween(0.0, 1.0);
    }

    @Test
    void equalScoresAreOrderedByMajorCodeSoResultsAreDeterministic() {
        Candidate b = major("B_MAJOR", map("I", 0.5));
        Candidate a = major("A_MAJOR", map("I", 0.5));

        List<Result> results = matcher.match(ANALYTICAL, List.of(b, a));

        assertThat(results.get(0).majorId()).isEqualTo(a.id());
    }

    // ---------- hard filter ----------

    @Test
    void inactiveMajorsAreRemoved() {
        Candidate inactive = new Candidate(DATA_SCIENCE.id(), "OFF", false, List.of("A00"), DATA_SCIENCE.profile(), List.of());

        assertThat(matcher.match(ANALYTICAL, List.of(inactive, ACCOUNTING))).extracting(Result::majorId)
                .containsExactly(ACCOUNTING.id());
    }

    @Test
    void majorsWithoutAnySharedExamCombinationAreRemoved() {
        Student c00 = withGpa(ANALYTICAL, List.of("C00"), Map.of());
        Candidate law = withCombos(major("LAW", map("E", 0.7)), List.of("A00", "C00", "D01"));

        List<Result> results = matcher.match(c00, List.of(law, ACCOUNTING, DATA_SCIENCE));

        assertThat(results).extracting(Result::majorId).containsExactly(law.id());
    }

    @Test
    void combinationMatchingIgnoresCaseAndSpaces() {
        Student sloppy = withGpa(ANALYTICAL, List.of(" d01 "), Map.of());

        assertThat(matcher.match(sloppy, List.of(ACCOUNTING))).hasSize(1);
    }

    @Test
    void noFilteringWhenTheStudentDeclaredNoCombination() {
        Candidate rare = withCombos(major("RARE", map("I", 0.5)), List.of("H00"));

        assertThat(matcher.match(ANALYTICAL, List.of(rare, ACCOUNTING))).hasSize(2);
    }

    @Test
    void aMajorWithoutListedCombinationsIsOpenToEveryone() {
        Candidate open = withCombos(major("OPEN", map("I", 0.5)), List.of());
        Student c00 = withGpa(ANALYTICAL, List.of("C00"), Map.of());

        assertThat(matcher.match(c00, List.of(open))).hasSize(1);
    }

    // ---------- soft penalties ----------

    private static Offering offering(String region, Long tuition, Double cutoff) {
        return new Offering(region, tuition, cutoff, 2025, "A00");
    }

    @Test
    void budgetPenaltyAppliesWhenEveryUniversityInTheChosenRegionsIsTooExpensive() {
        Candidate expensive = withOfferings(DATA_SCIENCE,
                offering("NORTH", 50_000_000L, null), offering("NORTH", 60_000_000L, null),
                offering("SOUTH", 20_000_000L, null));   // cheap, but not in the student's region
        Student northOnly = withBudget(ANALYTICAL, 30_000_000L, List.of("NORTH"));

        Result r = matcher.match(northOnly, List.of(expensive)).get(0);

        assertThat(r.penalties()).extracting(Penalty::code).containsExactly("BUDGET_EXCEEDED");
        assertThat(r.penalties().get(0).amount()).isEqualTo(0.10);
        assertThat(r.score()).isCloseTo(r.base() - 0.10, within(1e-9));
    }

    @Test
    void oneAffordableUniversityMeansNoBudgetPenalty() {
        Candidate mixed = withOfferings(DATA_SCIENCE, offering("NORTH", 50_000_000L, null), offering("NORTH", 25_000_000L, null));

        Result r = matcher.match(withBudget(ANALYTICAL, 30_000_000L, List.of("NORTH")), List.of(mixed)).get(0);

        assertThat(r.penalties()).isEmpty();
    }

    @Test
    void noRegionPreferenceMeansEveryRegionCounts() {
        Candidate cheapInSouth = withOfferings(DATA_SCIENCE, offering("NORTH", 50_000_000L, null), offering("SOUTH", 10_000_000L, null));

        assertThat(matcher.match(withBudget(ANALYTICAL, 30_000_000L, List.of()), List.of(cheapInSouth)).get(0).penalties())
                .isEmpty();
    }

    @Test
    void noBudgetPenaltyWithoutBudgetOrWithoutTuitionData() {
        Candidate expensive = withOfferings(DATA_SCIENCE, offering("NORTH", 90_000_000L, null));
        Candidate unknown = withOfferings(ACCOUNTING, offering("NORTH", null, null));

        assertThat(matcher.match(ANALYTICAL, List.of(expensive)).get(0).penalties()).isEmpty();
        assertThat(matcher.match(withBudget(ANALYTICAL, 10L, List.of()), List.of(unknown)).get(0).penalties()).isEmpty();
        assertThat(matcher.match(withBudget(ANALYTICAL, 10L, List.of("NORTH")), List.of(
                withOfferings(DATA_SCIENCE)))   // no offerings at all
                .get(0).penalties()).isEmpty();
    }

    @Test
    void scorePenaltyAppliesWhenTheBestComboIsMoreThanThreePointsBelowTheLowestCutoff() {
        Candidate hard = withOfferings(DATA_SCIENCE, offering("NORTH", null, 27.0), offering("SOUTH", null, 28.5));
        // D01 = 7 + 7 + 7 = 21 -> 6 points below the lowest cutoff of 27
        Student weak = withGpa(ANALYTICAL, List.of("D01"), Map.of("toan", 7.0, "van", 7.0, "anh", 7.0));

        Result r = matcher.match(weak, List.of(hard)).get(0);

        assertThat(r.penalties()).extracting(Penalty::code).containsExactly("SCORE_BELOW_CUTOFF");
        assertThat(r.penalties().get(0).amount()).isEqualTo(0.10);
    }

    @Test
    void exactlyThreePointsBelowIsNotPenalised() {
        Candidate borderline = withOfferings(DATA_SCIENCE, offering("NORTH", null, 24.0));
        Student student = withGpa(ANALYTICAL, List.of("D01"), Map.of("toan", 7.0, "van", 7.0, "anh", 7.0));   // 21

        assertThat(matcher.match(student, List.of(borderline)).get(0).penalties()).isEmpty();
    }

    @Test
    void theBestOfTheStudentsCombinationsIsUsedForTheComparison() {
        Candidate hard = withOfferings(DATA_SCIENCE, offering("NORTH", null, 27.0));
        Student student = withGpa(ANALYTICAL, List.of("A00", "D01"),
                map("toan", 9.0, "van", 8.5, "anh", 9.0, "ly", 5.0, "hoa", 5.0));   // A00 = 19, D01 = 26.5

        assertThat(matcher.match(student, List.of(hard)).get(0).penalties()).isEmpty();
    }

    @Test
    void noScorePenaltyWithoutGradesOrCutoffData() {
        Candidate hard = withOfferings(DATA_SCIENCE, offering("NORTH", null, 29.0));

        assertThat(matcher.match(ANALYTICAL, List.of(hard)).get(0).penalties()).isEmpty();
        assertThat(matcher.match(withGpa(ANALYTICAL, List.of("D01"), Map.of("toan", 5.0)), List.of(hard)).get(0).penalties())
                .isEmpty();   // grades incomplete
        assertThat(matcher.match(withGpa(ANALYTICAL, List.of("D01"), Map.of("toan", 5.0, "van", 5.0, "anh", 5.0)),
                List.of(withOfferings(DATA_SCIENCE, offering("NORTH", null, null)))).get(0).penalties()).isEmpty();
    }

    @Test
    void bothPenaltiesAddUpAndAreRecordedInTheBreakdown() {
        Candidate both = withOfferings(DATA_SCIENCE, offering("NORTH", 80_000_000L, 28.0));
        Student student = withBudget(withGpa(ANALYTICAL, List.of("D01"), Map.of("toan", 6.0, "van", 6.0, "anh", 6.0)),
                20_000_000L, List.of("NORTH"));

        Result r = matcher.match(student, List.of(both)).get(0);

        assertThat(r.penalties()).extracting(Penalty::code).containsExactly("BUDGET_EXCEEDED", "SCORE_BELOW_CUTOFF");
        assertThat(r.score()).isCloseTo(r.base() - 0.20, within(1e-9));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listed = (List<Map<String, Object>>) r.breakdown().get("penalties");
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0)).containsEntry("code", "BUDGET_EXCEEDED").containsEntry("amount", 0.1);
        assertThat(listed.get(0).get("detail")).asString().isNotBlank();
        assertThat(r.breakdown()).containsKeys("interest", "aptitude", "values", "base", "final");
    }

    @Test
    void finalScoreNeverGoesBelowZero() {
        Candidate hopeless = withOfferings(major("HOPELESS", map("S", 1.0)), offering("NORTH", 90_000_000L, 29.0));
        Student student = withBudget(withGpa(student(map("A", 0.01)), List.of("D01"),
                Map.of("toan", 1.0, "van", 1.0, "anh", 1.0)), 1L, List.of());

        Result r = matcher.match(student, List.of(hopeless)).get(0);

        assertThat(r.score()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void penaltiesChangeTheRanking() {
        // Same profile, so only the penalty can separate them; without it "A_EXPENSIVE" wins the tie on code.
        Candidate expensive = withOfferings(major("A_EXPENSIVE", DATA_SCIENCE.profile()), offering("NORTH", 90_000_000L, null));
        Candidate cheap = withOfferings(major("B_CHEAP", DATA_SCIENCE.profile()), offering("NORTH", 10_000_000L, null));
        Student student = withBudget(ANALYTICAL, 20_000_000L, List.of("NORTH"));

        List<Candidate> all = List.of(expensive, cheap);
        assertThat(code(matcher.match(ANALYTICAL, all).get(0), all)).isEqualTo("A_EXPENSIVE");
        assertThat(code(matcher.match(student, all).get(0), all)).isEqualTo("B_CHEAP");
    }

    // ---------- degenerate input ----------

    @Test
    void anAllZeroStudentVectorDoesNotCrashAndScoresZero() {
        List<Result> results = matcher.match(student(Map.of()), List.of(ACCOUNTING, DATA_SCIENCE));

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r -> {
            assertThat(r.score()).isEqualTo(0);
            assertThat(r.interest()).isEqualTo(0);
        });
    }

    @Test
    void aMajorWithAnEmptyProfileDoesNotCrash() {
        Result r = matcher.match(ANALYTICAL, List.of(major("EMPTY", Map.of()))).get(0);

        assertThat(r.score()).isEqualTo(0);
    }

    @Test
    void aStudentWithOnlyOneGroupStillGetsScoresFromThatGroup() {
        Result r = matcher.match(student(map("I", 1.0, "C", 1.0)), List.of(ACCOUNTING)).get(0);

        assertThat(r.interest()).isGreaterThan(0);
        assertThat(r.aptitude()).isEqualTo(0);
        assertThat(r.values()).isEqualTo(0);
        assertThat(r.score()).isCloseTo(0.5 * r.interest(), within(1e-9));
    }

    @Test
    void noCandidatesGivesNoResults() {
        assertThat(matcher.match(ANALYTICAL, List.of())).isEmpty();
    }

    @Test
    void cosineOfIdenticalDirectionsIsOneAndOfZeroVectorsIsZero() {
        assertThat(MajorMatcher.cosine(new double[] {1, 2, 3}, new double[] {2, 4, 6})).isCloseTo(1.0, within(1e-9));
        assertThat(MajorMatcher.cosine(new double[] {0, 0}, new double[] {1, 1})).isEqualTo(0);
        assertThat(MajorMatcher.cosine(new double[] {1, 0}, new double[] {0, 1})).isEqualTo(0);
    }
}
