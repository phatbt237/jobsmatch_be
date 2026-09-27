package vn.career.survey.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.api.ErrorDetail;
import vn.career.common.exception.AppException;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.exception.NotFoundException;
import vn.career.survey.api.dto.ProgressResponse;
import vn.career.survey.api.dto.SectionProgress;
import vn.career.survey.domain.Answer;
import vn.career.survey.domain.Question;
import vn.career.survey.domain.SurveyAttempt;
import vn.career.survey.domain.SurveySection;
import vn.career.survey.infrastructure.AnswerRepository;
import vn.career.survey.infrastructure.DimensionRepository;
import vn.career.survey.infrastructure.StudentConstraintsRepository;
import vn.career.survey.infrastructure.SurveyAttemptRepository;

@Service
@RequiredArgsConstructor
class DefaultSurveyAttemptApi implements SurveyAttemptApi {

    private final SurveyAttemptRepository attempts;
    private final AnswerRepository answers;
    private final StudentConstraintsRepository constraints;
    private final DimensionRepository dimensions;

    @Override
    @Transactional(readOnly = true)
    public AttemptInfo getAttempt(UUID attemptId) {
        return toInfo(find(attemptId, false));
    }

    @Override
    @Transactional
    public AttemptInfo lockForSubmission(UUID userId, UUID attemptId) {
        SurveyAttempt attempt = find(attemptId, true);
        if (!attempt.isOwnedBy(userId)) {
            throw new ForbiddenException("You do not have access to this attempt");
        }
        if (!attempt.isInProgress()) {
            throw new BusinessException(ErrorCode.ATTEMPT_NOT_EDITABLE, "This attempt has already been submitted");
        }
        Set<UUID> answered = new HashSet<>(answers.findAnsweredQuestionIds(attemptId));
        ProgressResponse progress = ProgressCalculator.calculate(
                attempt.getSurvey(), answered, constraints.existsById(attemptId));
        if (!progress.readyToSubmit()) {
            List<ErrorDetail> missing = new ArrayList<>();
            for (SectionProgress section : progress.sections()) {
                if (!section.complete()) {
                    missing.add(new ErrorDetail("section " + section.code(), section.requiredAnswered() + " of "
                            + section.requiredQuestions() + " required questions answered"));
                }
            }
            throw new AppException(ErrorCode.SURVEY_INCOMPLETE,
                    "Please finish every required question and section D before submitting", missing);
        }
        return toInfo(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public ScoringData loadScoringData(UUID attemptId) {
        SurveyAttempt attempt = find(attemptId, false);
        Map<UUID, Answer> byQuestion = answers.findAllOfAttempt(attemptId).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
        List<ScoringQuestion> questions = new ArrayList<>();
        for (SurveySection section : attempt.getSurvey().getSections()) {
            for (Question question : section.getQuestions()) {
                Answer answer = byQuestion.get(question.getId());
                questions.add(new ScoringQuestion(
                        question.getId(), question.getType().name(), question.getDimensionCode(), question.getWeight(),
                        question.isReverseScored(), question.isAttentionCheck(), question.getExpectedValue(),
                        question.getOptions().stream()
                                .map(o -> new ScoringOption(o.getId(), o.getOrderIndex(), o.getValue(), o.isCorrect()))
                                .toList(),
                        answer == null ? null : answer.getValue()));
            }
        }
        return new ScoringData(attemptId, attempt.getStartedAt(), questions);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConstraintsData> loadConstraints(UUID attemptId) {
        return constraints.findById(attemptId).map(c -> new ConstraintsData(
                c.getGpa(),
                c.getCombos() == null ? List.of() : List.of(c.getCombos()),
                c.getPreferredRegions() == null ? List.of() : List.of(c.getPreferredRegions()),
                c.getBudgetPerYear(),
                c.getFamilyPressure() == null ? null : c.getFamilyPressure().intValue()));
    }

    @Override
    @Transactional
    public void markScored(UUID attemptId, Instant submittedAt, Map<String, Object> qualityFlags) {
        find(attemptId, true).markScored(submittedAt, qualityFlags);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DimensionInfo> dimensions() {
        return dimensions.findAllByOrderByGroupAscCodeAsc().stream()
                .map(d -> new DimensionInfo(d.getCode(), d.getName(), d.getGroup().name()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> attemptIdsOf(UUID userId) {
        return attempts.findIdsByUserId(userId);
    }

    private SurveyAttempt find(UUID attemptId, boolean lock) {
        return (lock ? attempts.findForUpdate(attemptId) : attempts.findWithSurveyById(attemptId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.ATTEMPT_NOT_FOUND, "Attempt not found"));
    }

    private static AttemptInfo toInfo(SurveyAttempt attempt) {
        return new AttemptInfo(attempt.getId(), attempt.getUserId(), attempt.getStatus().name(),
                attempt.getSurvey().getVersion(), attempt.getStartedAt(), attempt.getSubmittedAt(),
                attempt.getQualityFlags());
    }
}
