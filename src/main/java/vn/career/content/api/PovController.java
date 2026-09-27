package vn.career.content.api;

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
import vn.career.content.api.dto.MyPovResponse;
import vn.career.content.api.dto.PovRequest;
import vn.career.content.api.dto.PovResponse;
import vn.career.content.application.PovService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "POV posts")
public class PovController {

    private final PovService povService;

    /** Published posts of a major. Public. Data flagged {@code sampleData} is invented development content. */
    @GetMapping("/majors/{majorId}/povs")
    public ApiResponse<PageResponse<PovResponse>> forMajor(@PathVariable UUID majorId,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(povService.publishedForMajor(majorId, PageRequests.of(page, size, Sort.unsorted())));
    }

    /** A published post. Public. Posts in any other state are reported as not found. */
    @GetMapping("/povs/{id}")
    public ApiResponse<PovResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(povService.published(id));
    }

    /** Creates a DRAFT. Only verified mentors. */
    @PostMapping("/povs")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<ApiResponse<MyPovResponse>> create(@Valid @RequestBody PovRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(povService.create(SecurityUtils.requireCurrentUser().id(), request)));
    }

    /** Edits a post while it is DRAFT or REJECTED. Only its author. */
    @PutMapping("/povs/{id}")
    @PreAuthorize("hasRole('MENTOR')")
    public ApiResponse<MyPovResponse> update(@PathVariable UUID id, @Valid @RequestBody PovRequest request) {
        return ApiResponse.ok(povService.update(SecurityUtils.requireCurrentUser().id(), id, request));
    }

    /** Sends the post for review. All five sections must have text. */
    @PostMapping("/povs/{id}/submit")
    @PreAuthorize("hasRole('MENTOR')")
    public ApiResponse<MyPovResponse> submit(@PathVariable UUID id) {
        return ApiResponse.ok(povService.submit(SecurityUtils.requireCurrentUser().id(), id));
    }
}
