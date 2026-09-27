package vn.career.survey.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.career.common.api.ApiResponse;
import vn.career.common.api.PageRequests;
import vn.career.common.api.PageResponse;
import vn.career.common.security.SecurityUtils;
import vn.career.survey.api.dto.AttemptDetailResponse;
import vn.career.survey.api.dto.AttemptResponse;
import vn.career.survey.api.dto.ConstraintsRequest;
import vn.career.survey.api.dto.ConstraintsResponse;
import vn.career.survey.api.dto.ProgressResponse;
import vn.career.survey.api.dto.SaveAnswersRequest;
import vn.career.survey.api.dto.SurveyResponse;
import vn.career.survey.application.AttemptService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Survey attempts")
public class AttemptController {

    private final AttemptService attemptService;

    /** Starts an attempt on the active survey, or returns the unfinished one (200 instead of 201). */
    @PostMapping("/attempts")
    public ResponseEntity<ApiResponse<AttemptResponse>> start() {
        AttemptService.StartResult result = attemptService.start(SecurityUtils.requireCurrentUser().id());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(result.attempt()));
    }

    /** Autosave: upserts a batch of answers. The whole batch is rejected if any answer is invalid. */
    @PutMapping("/attempts/{id}/answers")
    public ApiResponse<ProgressResponse> saveAnswers(@PathVariable UUID id, @Valid @RequestBody SaveAnswersRequest request) {
        return ApiResponse.ok(attemptService.saveAnswers(SecurityUtils.requireCurrentUser().id(), id, request));
    }

    /** Section D. Replaces the previously saved constraints. */
    @PutMapping("/attempts/{id}/constraints")
    public ApiResponse<ConstraintsResponse> saveConstraints(@PathVariable UUID id,
                                                            @Valid @RequestBody ConstraintsRequest request) {
        return ApiResponse.ok(attemptService.saveConstraints(SecurityUtils.requireCurrentUser().id(), id, request));
    }

    /** Progress plus everything saved so far, so the client can resume where the student stopped. */
    @GetMapping("/attempts/{id}")
    public ApiResponse<AttemptDetailResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(attemptService.get(SecurityUtils.requireCurrentUser().id(), id));
    }

    /** The questions of this attempt's survey version (use this, not /surveys/active, to render an attempt). */
    @GetMapping("/attempts/{id}/survey")
    public ApiResponse<SurveyResponse> survey(@PathVariable UUID id) {
        return ApiResponse.ok(attemptService.getSurvey(SecurityUtils.requireCurrentUser().id(), id));
    }

    @GetMapping("/me/attempts")
    public ApiResponse<PageResponse<AttemptResponse>> mine(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(attemptService.listMine(SecurityUtils.requireCurrentUser().id(),
                PageRequests.of(page, size, Sort.unsorted())));
    }
}
