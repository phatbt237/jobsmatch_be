package vn.career.recommendation.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.career.common.api.ApiResponse;
import vn.career.common.security.SecurityUtils;
import vn.career.recommendation.api.dto.ResultResponse;
import vn.career.recommendation.application.ResultService;
import vn.career.recommendation.application.ResultStreamService;
import vn.career.recommendation.application.SubmitService;

@RestController
@RequestMapping("/api/v1/attempts/{id}")
@RequiredArgsConstructor
@Tag(name = "Results")
public class SubmitController {

    private final SubmitService submitService;
    private final ResultService resultService;
    private final ResultStreamService streamService;

    /** Checks the attempt is complete, scores it, matches majors and returns the result at once (no waiting for AI). */
    @PostMapping("/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ResultResponse> submit(@PathVariable UUID id) {
        return ApiResponse.ok(submitService.submit(SecurityUtils.requireCurrentUser().id(), id));
    }

    /**
     * Scores, top majors with their breakdown and the explanation (which may still be PENDING).
     * Allowed for the student who owns the attempt, a parent linked to them and admins.
     */
    @GetMapping("/result")
    @PreAuthorize("hasAnyRole('STUDENT', 'PARENT', 'ADMIN')")
    public ApiResponse<ResultResponse> result(@PathVariable UUID id) {
        return ApiResponse.ok(resultService.getResult(SecurityUtils.requireCurrentUser(), id));
    }

    /** Server-sent events: one "explanation" event with the full result as soon as the explanation is final. */
    @GetMapping(value = "/result/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT', 'PARENT', 'ADMIN')")
    public SseEmitter stream(@PathVariable UUID id) {
        return streamService.open(SecurityUtils.requireCurrentUser(), id);
    }
}
