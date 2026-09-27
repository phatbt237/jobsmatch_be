package vn.career.recommendation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.common.event.DomainEventPublisher;
import vn.career.recommendation.api.dto.ResultResponse;
import vn.career.recommendation.domain.MajorMatcher;
import vn.career.recommendation.domain.Recommendation;
import vn.career.recommendation.infrastructure.RecommendationRepository;
import vn.career.scoring.application.DimensionResult;
import vn.career.scoring.application.ScoringResult;
import vn.career.scoring.application.ScoringService;
import vn.career.survey.application.SurveyAttemptApi;

/**
 * Submit flow. Everything up to the event runs in ONE transaction:
 * check and lock the attempt, score it, match majors, store the top recommendations, mark the attempt SCORED.
 * The event is published in that transaction but listeners only run after it commits, and the response returns
 * immediately without waiting for the LLM.
 */
@Service
@RequiredArgsConstructor
public class SubmitService {

    private final SurveyAttemptApi surveyApi;
    private final ScoringService scoringService;
    private final CatalogQueryApi catalogApi;
    private final RecommendationRepository recommendations;
    private final ResultService resultService;
    private final MatchingProperties matchingProperties;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Transactional
    public ResultResponse submit(UUID userId, UUID attemptId) {
        surveyApi.lockForSubmission(userId, attemptId);
        Instant now = Instant.now(clock);

        ScoringResult scoring = scoringService.scoreAttempt(attemptId, now);
        MajorMatcher matcher = new MajorMatcher(config());
        List<MajorMatcher.Result> matches = matcher.match(studentProfile(attemptId, scoring), candidates());

        recommendations.deleteByAttemptId(attemptId);
        recommendations.flush();
        recommendations.saveAll(matches.stream()
                .map(m -> Recommendation.create(attemptId, m, MajorMatcher.ALGO_VERSION))
                .toList());

        surveyApi.markScored(attemptId, now, scoring.qualityFlags());
        events.publish(new AttemptScoredEvent(attemptId, userId));
        recommendations.flush();
        return resultService.buildResult(attemptId);
    }

    private MajorMatcher.Config config() {
        List<SurveyAttemptApi.DimensionInfo> dimensions = surveyApi.dimensions();
        MajorMatcher.Layout layout = new MajorMatcher.Layout(
                codesOf(dimensions, "INTEREST"), codesOf(dimensions, "APTITUDE"), codesOf(dimensions, "VALUE"));
        return new MajorMatcher.Config(matchingProperties.interestWeight(), matchingProperties.aptitudeWeight(),
                matchingProperties.valuesWeight(), matchingProperties.budgetPenalty(), matchingProperties.scorePenalty(),
                matchingProperties.scoreGapThreshold(), matchingProperties.topN(), layout);
    }

    private static List<String> codesOf(List<SurveyAttemptApi.DimensionInfo> dimensions, String group) {
        return dimensions.stream().filter(d -> d.group().equals(group)).map(SurveyAttemptApi.DimensionInfo::code).toList();
    }

    private MajorMatcher.Student studentProfile(UUID attemptId, ScoringResult scoring) {
        Map<String, Double> scores = new HashMap<>();
        for (DimensionResult result : scoring.scores()) {
            scores.put(result.code(), result.normalized());
        }
        SurveyAttemptApi.ConstraintsData constraints = surveyApi.loadConstraints(attemptId).orElse(null);
        if (constraints == null) {
            return new MajorMatcher.Student(scores, List.of(), Map.of(), null, List.of());
        }
        Map<String, Double> gpa = constraints.gpa() == null ? Map.of() : constraints.gpa().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        return new MajorMatcher.Student(scores, constraints.combos(), gpa, constraints.budgetPerYear(),
                constraints.preferredRegions());
    }

    private List<MajorMatcher.Candidate> candidates() {
        return catalogApi.loadActiveMajorsForMatching().stream()
                .map(m -> new MajorMatcher.Candidate(m.id(), m.code(), m.active(), m.combos(),
                        m.profile().entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue())),
                        m.offerings().stream()
                                .map(o -> new MajorMatcher.Offering(o.region(), o.tuitionPerYear(),
                                        o.cutoffScore() == null ? null : o.cutoffScore().doubleValue(),
                                        o.year(), o.combo()))
                                .toList()))
                .toList();
    }
}
