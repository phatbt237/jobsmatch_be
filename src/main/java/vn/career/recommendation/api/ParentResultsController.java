package vn.career.recommendation.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.career.common.api.ApiResponse;
import vn.career.common.security.SecurityUtils;
import vn.career.recommendation.api.dto.ScoredAttemptSummary;
import vn.career.recommendation.application.ResultService;

@RestController
@RequestMapping("/api/v1/parent/students/{studentId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARENT')")
@Tag(name = "Parent link")
public class ParentResultsController {

    private final ResultService resultService;

    /** Finished surveys of a linked child. Open one with {@code GET /attempts/{id}/result} (that read is audited). */
    @GetMapping("/results")
    public ApiResponse<List<ScoredAttemptSummary>> results(@PathVariable UUID studentId) {
        return ApiResponse.ok(resultService.listScoredAttempts(SecurityUtils.requireCurrentUser(), studentId));
    }
}
