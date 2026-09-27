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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.api.ErrorDetail;
import vn.career.common.api.PageResponse;
import vn.career.common.exception.AppException;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.exception.NotFoundException;
import vn.career.survey.api.dto.AnswerItem;
import vn.career.survey.api.dto.AttemptDetailResponse;
import vn.career.survey.api.dto.AttemptResponse;
import vn.career.survey.api.dto.ConstraintsRequest;
import vn.career.survey.api.dto.ConstraintsResponse;
import vn.career.survey.api.dto.ProgressResponse;
import vn.career.survey.api.dto.SaveAnswersRequest;
import vn.career.survey.api.dto.SavedAnswer;
import vn.career.survey.api.dto.SurveyResponse;
import vn.career.survey.domain.Answer;
import vn.career.survey.domain.AnswerValidator;
import vn.career.survey.domain.AttemptStatus;
import vn.career.survey.domain.Question;
import vn.career.survey.domain.StudentConstraints;
import vn.career.survey.domain.Survey;
import vn.career.survey.domain.SurveyAttempt;
import vn.career.survey.domain.SurveyStatus;
import vn.career.survey.infrastructure.AnswerRepository;
import vn.career.survey.infrastructure.QuestionRepository;
import vn.career.survey.infrastructure.StudentConstraintsRepository;
import vn.career.survey.infrastructure.SurveyAttemptRepository;
import vn.career.survey.infrastructure.SurveyMapper;
import vn.career.survey.infrastructure.SurveyRepository;

/** A student's attempts: start, autosave answers and constraints, resume. Every method checks ownership. */
@Service
@RequiredArgsConstructor
public class AttemptService {

    /** Result of {@link #start}: {@code created} is false when an unfinished attempt was returned. */
    public record StartResult(AttemptResponse attempt, boolean created) {
    }

    private final SurveyRepository surveys;
    private final SurveyAttemptRepository attempts;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final StudentConstraintsRepository constraints;
    private final SurveyMapper mapper;
    private final Clock clock;

    @Transactional
    public StartResult start(UUID userId) {
        attempts.lockByKey("attempt:" + userId);
        Optional<SurveyAttempt> existing = attempts.findFirstByUserIdAndStatus(userId, AttemptStatus.IN_PROGRESS);
        if (existing.isPresent()) {
            return new StartResult(toResponse(existing.get()), false);
        }
        Survey survey = surveys.findFirstByStatus(SurveyStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NO_ACTIVE_SURVEY, "No survey is published right now"));
        SurveyAttempt attempt = attempts.save(SurveyAttempt.start(userId, survey, Instant.now(clock)));
        return new StartResult(toResponse(attempt), true);
    }

    @Transactional
    public ProgressResponse saveAnswers(UUID userId, UUID attemptId, SaveAnswersRequest request) {
        SurveyAttempt attempt = loadOwned(userId, attemptId, true);
        requireInProgress(attempt);

        List<ErrorDetail> problems = new ArrayList<>();
        Set<UUID> requestedIds = new HashSet<>();
        for (int i = 0; i < request.answers().size(); i++) {
            if (!requestedIds.add(request.answers().get(i).questionId())) {
                problems.add(new ErrorDetail("answers[" + i + "].questionId", "appears more than once in this request"));
            }
        }
        Map<UUID, Question> found = questions.findInSurvey(attempt.getSurvey().getId(), requestedIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        for (int i = 0; i < request.answers().size(); i++) {
            AnswerItem item = request.answers().get(i);
            Question question = found.get(item.questionId());
            if (question == null) {
                problems.add(new ErrorDetail("answers[" + i + "].questionId", "does not belong to this survey"));
            } else {
                int index = i;
                AnswerValidator.validate(question, item.value())
                        .ifPresent(message -> problems.add(new ErrorDetail("answers[" + index + "].value", message)));
            }
        }
        if (!problems.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Some answers are invalid", problems);
        }

        Instant now = Instant.now(clock);
        Map<UUID, Answer> existing = answers.findByAttemptAndQuestions(attemptId, requestedIds).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
        List<Answer> toSave = new ArrayList<>();
        for (AnswerItem item : request.answers()) {
            Answer answer = existing.get(item.questionId());
            if (answer == null) {
                toSave.add(Answer.create(attempt, found.get(item.questionId()), item.value(), now));
            } else {
                answer.update(item.value(), now);
            }
        }
        answers.saveAll(toSave);
        answers.flush();
        return progressOf(attempt);
    }

    @Transactional
    public ConstraintsResponse saveConstraints(UUID userId, UUID attemptId, ConstraintsRequest request) {
        SurveyAttempt attempt = loadOwned(userId, attemptId, true);
        requireInProgress(attempt);

        StudentConstraints entity = constraints.findById(attemptId).orElseGet(() -> new StudentConstraints(attemptId));
        entity.replaceWith(
                request.gpa(),
                toArray(request.combos()),
                toArray(request.preferredRegions()),
                request.budgetPerYear(),
                request.familyPressure() == null ? null : request.familyPressure().shortValue());
        return mapper.toConstraintsResponse(constraints.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public AttemptDetailResponse get(UUID userId, UUID attemptId) {
        SurveyAttempt attempt = loadOwned(userId, attemptId, false);
        List<SavedAnswer> saved = answers.findAllOfAttempt(attemptId).stream()
                .map(a -> new SavedAnswer(a.getQuestion().getId(), a.getValue(), a.getAnsweredAt()))
                .toList();
        ConstraintsResponse savedConstraints = constraints.findById(attemptId)
                .map(mapper::toConstraintsResponse).orElse(null);
        return new AttemptDetailResponse(toResponse(attempt), progressOf(attempt), saved, savedConstraints);
    }

    /**
     * The survey exactly as this attempt started with it. It can differ from the active survey when a newer
     * version was published while the student was still answering.
     */
    @Transactional(readOnly = true)
    public SurveyResponse getSurvey(UUID userId, UUID attemptId) {
        Survey survey = loadOwned(userId, attemptId, false).getSurvey();
        return new SurveyResponse(survey.getId(), survey.getVersion(), survey.getTitle(), SurveyService.LIKERT_SCALE,
                survey.getSections().stream().map(mapper::toStudentSection).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<AttemptResponse> listMine(UUID userId, Pageable pageable) {
        return PageResponse.from(attempts.findByUserIdOrderByStartedAtDesc(userId, pageable).map(this::toResponse));
    }

    private SurveyAttempt loadOwned(UUID userId, UUID attemptId, boolean lock) {
        SurveyAttempt attempt = (lock ? attempts.findForUpdate(attemptId) : attempts.findWithSurveyById(attemptId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.ATTEMPT_NOT_FOUND, "Attempt not found"));
        if (!attempt.isOwnedBy(userId)) {
            throw new ForbiddenException("You do not have access to this attempt");
        }
        return attempt;
    }

    private static void requireInProgress(SurveyAttempt attempt) {
        if (!attempt.isInProgress()) {
            throw new BusinessException(ErrorCode.ATTEMPT_NOT_EDITABLE, "This attempt has already been submitted");
        }
    }

    private ProgressResponse progressOf(SurveyAttempt attempt) {
        Set<UUID> answered = new HashSet<>(answers.findAnsweredQuestionIds(attempt.getId()));
        return ProgressCalculator.calculate(attempt.getSurvey(), answered, constraints.existsById(attempt.getId()));
    }

    private AttemptResponse toResponse(SurveyAttempt attempt) {
        return new AttemptResponse(attempt.getId(), attempt.getSurvey().getId(), attempt.getSurvey().getVersion(),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getSubmittedAt());
    }

    private static String[] toArray(List<String> values) {
        return values == null ? null : values.stream().distinct().toArray(String[]::new);
    }
}
