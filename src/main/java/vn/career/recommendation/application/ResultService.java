package vn.career.recommendation.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.application.AccountAccessApi;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.security.AuthenticatedUser;
import vn.career.recommendation.api.dto.DimensionScoreResponse;
import vn.career.recommendation.api.dto.RecommendationResponse;
import vn.career.recommendation.api.dto.ResultResponse;
import vn.career.recommendation.api.dto.ScoredAttemptSummary;
import vn.career.recommendation.domain.ExplanationStatus;
import vn.career.recommendation.domain.MajorMatcher;
import vn.career.recommendation.domain.Recommendation;
import vn.career.recommendation.infrastructure.RecommendationRepository;
import vn.career.recommendation.infrastructure.RecommendationSummaryRepository;
import vn.career.scoring.application.ScoringService;
import vn.career.survey.application.SurveyAttemptApi;

/** Builds the result of a scored attempt and decides who may look at it. */
@Service
@RequiredArgsConstructor
public class ResultService {

    static final String DISCLAIMER = "Đây chỉ là gợi ý tham khảo dựa trên câu trả lời của bạn. "
            + "Hãy đọc thêm chia sẻ của những người đang làm trong ngành và trao đổi với thầy cô, gia đình trước khi quyết định.";

    private final SurveyAttemptApi surveyApi;
    private final ScoringService scoringService;
    private final CatalogQueryApi catalogApi;
    private final RecommendationRepository recommendations;
    private final RecommendationSummaryRepository summaries;
    private final AccountAccessApi accountAccess;
    private final AuditService auditService;

    /**
     * Result for a caller. Allowed: the student who owns the attempt, a parent with an APPROVED link to that student,
     * and admins. Looking at someone else's result is written to the audit log.
     */
    @Transactional
    public ResultResponse getResult(AuthenticatedUser caller, UUID attemptId) {
        SurveyAttemptApi.AttemptInfo info = surveyApi.getAttempt(attemptId);
        boolean owner = info.userId().equals(caller.id());
        boolean allowed = owner
                || "ADMIN".equals(caller.role())
                || ("PARENT".equals(caller.role()) && accountAccess.isApprovedParentOf(caller.id(), info.userId()));
        if (!allowed) {
            throw new ForbiddenException("You do not have access to this result");
        }
        if (!"SCORED".equals(info.status())) {
            throw new BusinessException(ErrorCode.RESULT_NOT_READY, "This attempt has not been submitted yet");
        }
        if (!owner) {
            auditService.record(AuditAction.RESULT_VIEWED_BY_OTHER, "SurveyAttempt", attemptId,
                    Map.of("studentId", info.userId().toString(), "viewerRole", caller.role()));
        }
        return buildResult(info);
    }

    /**
     * Finished surveys of a child, newest first, for a parent with an APPROVED link. Only ids and dates are returned;
     * the audited read of the actual result happens in {@link #getResult}.
     */
    @Transactional(readOnly = true)
    public List<ScoredAttemptSummary> listScoredAttempts(AuthenticatedUser caller, UUID studentId) {
        if (!accountAccess.isApprovedParentOf(caller.id(), studentId)) {
            throw new ForbiddenException("You are not linked to this student");
        }
        return surveyApi.attemptIdsOf(studentId).stream()
                .map(surveyApi::getAttempt)
                .filter(info -> "SCORED".equals(info.status()))
                .map(info -> new ScoredAttemptSummary(info.attemptId(), info.submittedAt(), info.surveyVersion()))
                .toList();
    }

    /** Builds the result without any access check. Callers must have checked access already. */
    @Transactional(readOnly = true)
    public ResultResponse buildResult(UUID attemptId) {
        return buildResult(surveyApi.getAttempt(attemptId));
    }

    private ResultResponse buildResult(SurveyAttemptApi.AttemptInfo info) {
        Map<String, SurveyAttemptApi.DimensionInfo> dimensionInfo = surveyApi.dimensions().stream()
                .collect(Collectors.toMap(SurveyAttemptApi.DimensionInfo::code, d -> d));
        List<DimensionScoreResponse> dimensions = scoringService.getScores(info.attemptId()).stream()
                .map(s -> {
                    SurveyAttemptApi.DimensionInfo d = dimensionInfo.get(s.code());
                    return new DimensionScoreResponse(s.code(), d == null ? s.code() : d.name(),
                            d == null ? null : d.group(), Math.round(s.normalized() * 1000.0) / 10.0);
                })
                .toList();

        List<Recommendation> rows = recommendations.findByAttemptIdOrderByRankPosition(info.attemptId());
        Set<UUID> majorIds = rows.stream().map(Recommendation::getMajorId).collect(Collectors.toSet());
        Map<UUID, CatalogQueryApi.MajorSummary> majors = catalogApi.summaries(majorIds);
        List<RecommendationResponse> items = rows.stream().map(r -> {
            CatalogQueryApi.MajorSummary m = majors.get(r.getMajorId());
            return new RecommendationResponse(r.getRankPosition(), r.getMajorId(),
                    m == null ? null : m.code(), m == null ? "(unknown major)" : m.name(),
                    m == null ? null : m.groupName(), r.getScore().doubleValue(), r.getScoreBreakdown(),
                    r.getExplanation(), r.getExplanationStatus().name());
        }).toList();

        String status = rows.isEmpty() ? "NONE" : rows.get(0).getExplanationStatus().name();
        String summary = summaries.findById(info.attemptId()).map(s -> s.getSummary()).orElse(null);
        Map<String, Object> flags = info.qualityFlags() == null ? Map.of() : info.qualityFlags();
        boolean lowReliability = Boolean.TRUE.equals(flags.get("lowReliability"));
        return new ResultResponse(info.attemptId(), info.status(), info.submittedAt(), lowReliability, flags,
                dimensions, items, status, summary, MajorMatcher.ALGO_VERSION, DISCLAIMER);
    }

    public static boolean isPending(ResultResponse result) {
        return ExplanationStatus.PENDING.name().equals(result.explanationStatus());
    }
}
