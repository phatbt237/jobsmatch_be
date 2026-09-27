package vn.career.survey.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.career.common.api.ApiResponse;
import vn.career.survey.api.dto.SurveyResponse;
import vn.career.survey.application.SurveyService;

@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
@Tag(name = "Survey")
public class SurveyController {

    private final SurveyService surveyService;

    /** The PUBLISHED survey with sections, questions and options. Correct answers and scoring data are never included. */
    @GetMapping("/active")
    public ApiResponse<SurveyResponse> active() {
        return ApiResponse.ok(surveyService.getActive());
    }
}
