package vn.career.survey.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.NotFoundException;
import vn.career.survey.api.dto.LikertOption;
import vn.career.survey.api.dto.SurveyResponse;
import vn.career.survey.domain.Survey;
import vn.career.survey.domain.SurveyStatus;
import vn.career.survey.infrastructure.SurveyMapper;
import vn.career.survey.infrastructure.SurveyRepository;

/** Read access to the survey students take. */
@Service
@RequiredArgsConstructor
public class SurveyService {

    static final List<LikertOption> LIKERT_SCALE = List.of(
            new LikertOption(1, "Hoàn toàn không đồng ý"),
            new LikertOption(2, "Không đồng ý"),
            new LikertOption(3, "Bình thường"),
            new LikertOption(4, "Đồng ý"),
            new LikertOption(5, "Hoàn toàn đồng ý"));

    private final SurveyRepository surveys;
    private final SurveyMapper mapper;

    @Transactional(readOnly = true)
    public SurveyResponse getActive() {
        Survey survey = surveys.findFirstByStatus(SurveyStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NO_ACTIVE_SURVEY, "No survey is published right now"));
        return new SurveyResponse(survey.getId(), survey.getVersion(), survey.getTitle(), LIKERT_SCALE,
                survey.getSections().stream().map(mapper::toStudentSection).toList());
    }
}
