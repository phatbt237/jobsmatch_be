package vn.career.survey.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.api.ErrorDetail;
import vn.career.common.api.PageResponse;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.exception.AppException;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.NotFoundException;
import vn.career.survey.api.dto.AdminQuestionResponse;
import vn.career.survey.api.dto.AdminSectionResponse;
import vn.career.survey.api.dto.AdminSurveyResponse;
import vn.career.survey.api.dto.AdminSurveySummary;
import vn.career.survey.api.dto.DimensionResponse;
import vn.career.survey.api.dto.OptionRequest;
import vn.career.survey.api.dto.QuestionRequest;
import vn.career.survey.api.dto.SectionRequest;
import vn.career.survey.domain.Question;
import vn.career.survey.domain.QuestionRules;
import vn.career.survey.domain.QuestionSpec;
import vn.career.survey.domain.Survey;
import vn.career.survey.domain.SurveySection;
import vn.career.survey.domain.SurveyStatus;
import vn.career.survey.infrastructure.DimensionRepository;
import vn.career.survey.infrastructure.QuestionRepository;
import vn.career.survey.infrastructure.SurveyMapper;
import vn.career.survey.infrastructure.SurveyRepository;
import vn.career.survey.infrastructure.SurveySectionRepository;

/**
 * Admin editing of surveys. Structure can only change while a survey is DRAFT; publishing freezes it and
 * archives the previously published version. Existing attempts stay attached to the version they started with.
 */
@Service
@RequiredArgsConstructor
public class SurveyAdminService {

    private final SurveyRepository surveys;
    private final SurveySectionRepository sections;
    private final QuestionRepository questions;
    private final DimensionRepository dimensions;
    private final SurveyMapper mapper;
    private final AuditService auditService;
    private final Clock clock;

    // ---------------------------------------------------------------- dimensions

    @Transactional(readOnly = true)
    public List<DimensionResponse> listDimensions() {
        return dimensions.findAllByOrderByGroupAscCodeAsc().stream().map(mapper::toDimensionResponse).toList();
    }

    // ---------------------------------------------------------------- surveys

    @Transactional(readOnly = true)
    public PageResponse<AdminSurveySummary> list(Pageable pageable) {
        return PageResponse.from(surveys.findSummaries(pageable));
    }

    @Transactional(readOnly = true)
    public AdminSurveyResponse get(UUID surveyId) {
        return mapper.toAdminSurvey(findSurvey(surveyId));
    }

    @Transactional
    public AdminSurveyResponse create(String title) {
        Survey survey = surveys.save(Survey.draft(surveys.maxVersion() + 1, title.trim()));
        return mapper.toAdminSurvey(survey);
    }

    @Transactional
    public AdminSurveyResponse rename(UUID surveyId, String title) {
        Survey survey = findSurvey(surveyId);
        survey.rename(title.trim());
        return mapper.toAdminSurvey(survey);
    }

    @Transactional
    public void delete(UUID surveyId) {
        Survey survey = findSurvey(surveyId);
        survey.assertEditable();
        surveys.delete(survey);
    }

    /** Deep-copies any survey (usually the published one) into a new DRAFT with the next version number. */
    @Transactional
    public AdminSurveyResponse createNewVersion(UUID surveyId) {
        Survey source = findSurvey(surveyId);
        Survey copy = surveys.save(source.copyAsDraft(surveys.maxVersion() + 1));
        return mapper.toAdminSurvey(copy);
    }

    @Transactional
    public AdminSurveyResponse publish(UUID surveyId) {
        Survey survey = findSurvey(surveyId);
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new BusinessException(ErrorCode.SURVEY_NOT_PUBLISHABLE, "Only DRAFT surveys can be published");
        }
        if (survey.questionCount() == 0) {
            throw new BusinessException(ErrorCode.SURVEY_NOT_PUBLISHABLE, "A survey needs at least one question");
        }

        // Archive the current one first and flush: only one PUBLISHED row may exist at any moment.
        surveys.findFirstByStatus(SurveyStatus.PUBLISHED).ifPresent(current -> {
            current.archive();
            surveys.flush();
        });
        survey.publish(Instant.now(clock));
        surveys.flush();
        auditService.record(AuditAction.SURVEY_PUBLISHED, "Survey", survey.getId(),
                Map.of("version", survey.getVersion()));
        return mapper.toAdminSurvey(survey);
    }

    // ---------------------------------------------------------------- sections

    @Transactional
    public AdminSectionResponse addSection(UUID surveyId, SectionRequest request) {
        Survey survey = findSurvey(surveyId);
        survey.assertEditable();
        int order = request.orderIndex() != null ? request.orderIndex() : sections.maxOrderIndex(surveyId) + 1;
        checkSectionUniqueness(surveyId, request.code(), order, null);
        SurveySection section = survey.addSection(order, request.code(), request.title().trim(), request.description());
        // flush() not saveAndFlush(): save() on a managed entity would merge and hand back a copy of the new child
        surveys.flush();
        return mapper.toAdminSection(section);
    }

    @Transactional
    public AdminSectionResponse updateSection(UUID sectionId, SectionRequest request) {
        SurveySection section = findSection(sectionId);
        section.getSurvey().assertEditable();
        int order = request.orderIndex() != null ? request.orderIndex() : section.getOrderIndex();
        checkSectionUniqueness(section.getSurvey().getId(), request.code(), order, sectionId);
        section.update(order, request.code(), request.title().trim(), request.description());
        sections.flush();
        return mapper.toAdminSection(section);
    }

    @Transactional
    public void deleteSection(UUID sectionId) {
        SurveySection section = findSection(sectionId);
        section.getSurvey().removeSection(section);
    }

    // ---------------------------------------------------------------- questions

    @Transactional(readOnly = true)
    public AdminQuestionResponse getQuestion(UUID questionId) {
        return mapper.toAdminQuestion(findQuestion(questionId));
    }

    @Transactional
    public AdminQuestionResponse createQuestion(QuestionRequest request) {
        if (request.sectionId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new ErrorDetail("sectionId", "is required when creating a question")));
        }
        SurveySection section = findSection(request.sectionId());
        section.getSurvey().assertEditable();
        int order = request.orderIndex() != null ? request.orderIndex() : questions.maxOrderIndex(section.getId()) + 1;
        if (questions.existsBySectionIdAndOrderIndexAndIdNot(section.getId(), order, new UUID(0, 0))) {
            throw duplicateOrder();
        }
        Question question = section.addQuestion(toSpec(request, order));
        sections.flush();   // assigns the new question's id, see addSection
        return mapper.toAdminQuestion(question);
    }

    @Transactional
    public AdminQuestionResponse updateQuestion(UUID questionId, QuestionRequest request) {
        Question question = findQuestion(questionId);
        question.getSection().getSurvey().assertEditable();
        int order = request.orderIndex() != null ? request.orderIndex() : question.getOrderIndex();
        if (questions.existsBySectionIdAndOrderIndexAndIdNot(question.getSection().getId(), order, questionId)) {
            throw duplicateOrder();
        }
        question.apply(toSpec(request, order));
        questions.flush();
        return mapper.toAdminQuestion(question);
    }

    @Transactional
    public void deleteQuestion(UUID questionId) {
        Question question = findQuestion(questionId);
        question.getSection().removeQuestion(question);
    }

    // ---------------------------------------------------------------- helpers

    private QuestionSpec toSpec(QuestionRequest request, int orderIndex) {
        List<QuestionSpec.OptionSpec> options = new ArrayList<>();
        if (request.options() != null) {
            for (OptionRequest option : request.options()) {
                options.add(new QuestionSpec.OptionSpec(option.label().trim(), option.value(),
                        Boolean.TRUE.equals(option.correct())));
            }
        }
        String dimension = request.dimensionCode() == null || request.dimensionCode().isBlank()
                ? null : request.dimensionCode().trim();
        QuestionSpec spec = new QuestionSpec(
                request.type(),
                request.content().trim(),
                dimension,
                request.weight() != null ? request.weight() : BigDecimal.ONE,
                Boolean.TRUE.equals(request.reverseScored()),
                Boolean.TRUE.equals(request.attentionCheck()),
                request.expectedValue() == null || request.expectedValue().isNull() ? null : request.expectedValue(),
                request.required() == null || request.required(),
                orderIndex,
                options);
        List<ErrorDetail> problems = QuestionRules.validate(spec, dimensions::existsById);
        if (!problems.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Question definition is invalid", problems);
        }
        return spec;
    }

    private void checkSectionUniqueness(UUID surveyId, String code, int order, UUID excludeId) {
        UUID exclude = excludeId != null ? excludeId : new UUID(0, 0);
        if (sections.existsBySurveyIdAndCodeAndIdNot(surveyId, code, exclude)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SECTION_CODE, "This survey already has a section with code " + code);
        }
        if (sections.existsBySurveyIdAndOrderIndexAndIdNot(surveyId, order, exclude)) {
            throw duplicateOrder();
        }
    }

    private static BusinessException duplicateOrder() {
        return new BusinessException(ErrorCode.DUPLICATE_ORDER_INDEX, "Another item already uses this orderIndex");
    }

    private Survey findSurvey(UUID id) {
        return surveys.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SURVEY_NOT_FOUND, "Survey not found"));
    }

    private SurveySection findSection(UUID id) {
        return sections.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SECTION_NOT_FOUND, "Section not found"));
    }

    private Question findQuestion(UUID id) {
        return questions.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.QUESTION_NOT_FOUND, "Question not found"));
    }
}
