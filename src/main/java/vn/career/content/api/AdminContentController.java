package vn.career.content.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.career.common.api.ApiResponse;
import vn.career.common.api.PageRequests;
import vn.career.common.api.PageResponse;
import vn.career.common.security.SecurityUtils;
import vn.career.content.api.dto.AdminPovResponse;
import vn.career.content.api.dto.MentorResponse;
import vn.career.content.api.dto.ModerationRequest;
import vn.career.content.api.dto.PovStatusRequest;
import vn.career.content.api.dto.VerifyMentorRequest;
import vn.career.content.application.MentorService;
import vn.career.content.application.PovService;
import vn.career.content.application.QaService;
import vn.career.content.domain.PovStatus;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - content")
public class AdminContentController {

    private final PovService povService;
    private final MentorService mentorService;
    private final QaService qaService;

    /** Posts to review. Use {@code status=PENDING_REVIEW} for the queue. */
    @GetMapping("/povs")
    public ApiResponse<PageResponse<AdminPovResponse>> povs(@RequestParam(required = false) PovStatus status,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(povService.listForReview(status, PageRequests.of(page, size, Sort.unsorted())));
    }

    /** Approves (PUBLISHED) or rejects (REJECTED, reason required) a post waiting for review. Audited. */
    @PatchMapping("/povs/{id}/status")
    public ApiResponse<AdminPovResponse> reviewPov(@PathVariable UUID id, @Valid @RequestBody PovStatusRequest request) {
        return ApiResponse.ok(povService.review(SecurityUtils.requireCurrentUser().id(), id, request.status(), request.reason()));
    }

    @GetMapping("/mentors")
    public ApiResponse<PageResponse<MentorResponse>> mentors(@RequestParam(required = false) Boolean verified,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(mentorService.list(verified, PageRequests.of(page, size, Sort.unsorted())));
    }

    /** {id} is the mentor's user id. Audited. */
    @PatchMapping("/mentors/{id}/verify")
    public ApiResponse<MentorResponse> verifyMentor(@PathVariable UUID id, @RequestBody(required = false) VerifyMentorRequest request) {
        return ApiResponse.ok(mentorService.verify(SecurityUtils.requireCurrentUser().id(), id,
                request == null ? null : request.verified()));
    }

    @PatchMapping("/qa/threads/{id}")
    public ApiResponse<Void> moderateThread(@PathVariable UUID id, @Valid @RequestBody ModerationRequest request) {
        qaService.moderateThread(id, request.status());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/qa/answers/{id}")
    public ApiResponse<Void> moderateAnswer(@PathVariable UUID id, @Valid @RequestBody ModerationRequest request) {
        qaService.moderateAnswer(id, request.status());
        return ApiResponse.ok(null);
    }
}
