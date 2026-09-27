package vn.career.recommendation.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ranks majors for a student. Deterministic Java only; no LLM is involved in deciding what fits.
 *
 * <pre>
 * s_interest, s_aptitude, s_values = cosine similarity of the student's and the major's vectors (0 if a vector is all zero)
 * base   = w1 x s_interest + w2 x s_aptitude + w3 x s_values
 * HARD FILTER: inactive majors; majors whose exam combinations share nothing with the ones the student declared
 * SOFT PENALTY (subtracted, listed in the breakdown):
 *   BUDGET_EXCEEDED    every university in the student's regions charges more than the budget
 *   SCORE_BELOW_CUTOFF the student's best combination total is more than N points below the lowest latest-year cutoff
 * final  = max(0, base - sum of penalties); the best topN by final score are returned
 * </pre>
 */
public final class MajorMatcher {

    public static final String ALGO_VERSION = "v1";

    /** Which dimension codes belong to each group, in a fixed order shared by students and majors. */
    public record Layout(List<String> interest, List<String> aptitude, List<String> values) {
    }

    public record Config(double interestWeight, double aptitudeWeight, double valuesWeight,
                         double budgetPenalty, double scorePenalty, double scoreGapThreshold,
                         int topN, Layout layout) {
    }

    /** Scores are normalised 0..1 per dimension; gpa maps subject code to grade; regions are NORTH/CENTRAL/SOUTH. */
    public record Student(Map<String, Double> scores, List<String> combos, Map<String, Double> gpa,
                          Long budgetPerYear, List<String> regions) {
    }

    /** One university offering of the latest year. Cutoff and tuition may be unknown (null). */
    public record Offering(String region, Long tuitionPerYear, Double cutoffScore, int year, String combo) {
    }

    public record Candidate(UUID id, String code, boolean active, List<String> combos,
                            Map<String, Double> profile, List<Offering> offerings) {
    }

    public record Penalty(String code, double amount, String detail) {
    }

    public record Result(UUID majorId, int rank, double score, double base, double interest, double aptitude,
                         double values, List<Penalty> penalties) {

        /** JSON stored in recommendations.score_breakdown. */
        public Map<String, Object> breakdown() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("interest", round(interest));
            map.put("aptitude", round(aptitude));
            map.put("values", round(values));
            map.put("base", round(base));
            List<Map<String, Object>> penaltyList = new ArrayList<>();
            for (Penalty p : penalties) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("code", p.code());
                entry.put("amount", round(p.amount()));
                entry.put("detail", p.detail());
                penaltyList.add(entry);
            }
            map.put("penalties", penaltyList);
            map.put("final", round(score));
            return map;
        }
    }

    private final Config config;

    public MajorMatcher(Config config) {
        this.config = config;
    }

    public List<Result> match(Student student, List<Candidate> candidates) {
        List<Result> unranked = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (isExcluded(student, candidate)) {
                continue;
            }
            double interest = cosine(vector(student.scores(), config.layout().interest()),
                    vector(candidate.profile(), config.layout().interest()));
            double aptitude = cosine(vector(student.scores(), config.layout().aptitude()),
                    vector(candidate.profile(), config.layout().aptitude()));
            double values = cosine(vector(student.scores(), config.layout().values()),
                    vector(candidate.profile(), config.layout().values()));
            double base = config.interestWeight() * interest + config.aptitudeWeight() * aptitude
                    + config.valuesWeight() * values;
            List<Penalty> penalties = penaltiesFor(student, candidate);
            double totalPenalty = penalties.stream().mapToDouble(Penalty::amount).sum();
            double score = Math.max(0, base - totalPenalty);
            unranked.add(new Result(candidate.id(), 0, score, base, interest, aptitude, values, penalties));
        }

        Map<UUID, String> codes = candidates.stream().collect(Collectors.toMap(Candidate::id, Candidate::code, (a, b) -> a));
        List<Result> sorted = unranked.stream()
                .sorted(Comparator.comparingDouble(Result::score).reversed()
                        .thenComparing(Comparator.comparingDouble(Result::base).reversed())
                        .thenComparing(r -> codes.get(r.majorId())))
                .limit(config.topN())
                .toList();
        List<Result> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Result r = sorted.get(i);
            ranked.add(new Result(r.majorId(), i + 1, r.score(), r.base(), r.interest(), r.aptitude(), r.values(),
                    r.penalties()));
        }
        return ranked;
    }

    // ---------------------------------------------------------------- hard filter

    private static boolean isExcluded(Student student, Candidate candidate) {
        if (!candidate.active()) {
            return true;
        }
        // A major without any listed combination is treated as open to every combination.
        if (!student.combos().isEmpty() && !candidate.combos().isEmpty()) {
            Set<String> mine = normalized(student.combos());
            return candidate.combos().stream().map(MajorMatcher::normalize).noneMatch(mine::contains);
        }
        return false;
    }

    // ---------------------------------------------------------------- soft penalties

    private List<Penalty> penaltiesFor(Student student, Candidate candidate) {
        List<Penalty> penalties = new ArrayList<>();
        budgetPenalty(student, candidate).ifPresent(penalties::add);
        scorePenalty(student, candidate).ifPresent(penalties::add);
        return penalties;
    }

    private java.util.Optional<Penalty> budgetPenalty(Student student, Candidate candidate) {
        if (student.budgetPerYear() == null) {
            return java.util.Optional.empty();
        }
        Set<String> regions = student.regions().stream().map(r -> r.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        List<Long> tuitions = candidate.offerings().stream()
                .filter(o -> regions.isEmpty() || regions.contains(o.region().toUpperCase(Locale.ROOT)))
                .map(Offering::tuitionPerYear)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!tuitions.isEmpty() && tuitions.stream().allMatch(t -> t > student.budgetPerYear())) {
            long cheapest = tuitions.stream().mapToLong(Long::longValue).min().orElseThrow();
            return java.util.Optional.of(new Penalty("BUDGET_EXCEEDED", config.budgetPenalty(),
                    "Every university in the chosen regions costs more than the budget (cheapest: " + cheapest
                            + " VND per year, budget: " + student.budgetPerYear() + ")"));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Penalty> scorePenalty(Student student, Candidate candidate) {
        Set<String> mine = normalized(student.combos());
        List<String> combos = mine.isEmpty()
                ? candidate.combos()
                : candidate.combos().isEmpty()
                        ? List.copyOf(mine)
                        : candidate.combos().stream().filter(c -> mine.contains(normalize(c))).toList();
        OptionalDouble expected = ExamCombos.best(student.gpa(), combos);
        OptionalDouble lowestCutoff = candidate.offerings().stream()
                .map(Offering::cutoffScore).filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue).min();
        if (expected.isPresent() && lowestCutoff.isPresent()
                && lowestCutoff.getAsDouble() - expected.getAsDouble() > config.scoreGapThreshold()) {
            return java.util.Optional.of(new Penalty("SCORE_BELOW_CUTOFF", config.scorePenalty(),
                    "Expected total " + round(expected.getAsDouble()) + " is more than " + config.scoreGapThreshold()
                            + " points below the lowest recent cutoff " + round(lowestCutoff.getAsDouble())));
        }
        return java.util.Optional.empty();
    }

    // ---------------------------------------------------------------- math helpers

    private static double[] vector(Map<String, Double> values, List<String> dimensions) {
        double[] vector = new double[dimensions.size()];
        for (int i = 0; i < vector.length; i++) {
            Double value = values.get(dimensions.get(i));
            vector[i] = value == null ? 0 : value;
        }
        return vector;
    }

    /** Cosine similarity, 0 when either vector is all zeros (undefined mathematically, never an error). */
    static double cosine(double[] a, double[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static Set<String> normalized(List<String> combos) {
        return combos.stream().map(MajorMatcher::normalize).collect(Collectors.toSet());
    }

    private static String normalize(String combo) {
        return combo.trim().toUpperCase(Locale.ROOT);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
