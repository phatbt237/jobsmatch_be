package vn.career.content.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.career.common.api.ApiResponse;
import vn.career.common.api.PageRequests;
import vn.career.common.api.PageResponse;
import vn.career.common.security.SecurityUtils;
import vn.career.content.api.dto.MentorApplyRequest;
import vn.career.content.api.dto.MentorResponse;
import vn.career.content.api.dto.MyPovResponse;
import vn.career.content.application.MentorService;
import vn.career.content.application.PovService;

@RestController
@RequestMapping("/api/v1/mentor")
@RequiredArgsConstructor
@Tag(name = "Mentor")
public class MentorController {

    private final MentorService mentorService;
    private final PovService povService;

    /**
     * An adult (PARENT) account applies to become a mentor and gets the MENTOR role, but cannot publish until an
     * admin verifies the profile. Refresh the access token afterwards to see the new role.
     */
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<MentorResponse>> apply(@Valid @RequestBody MentorApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(mentorService.apply(SecurityUtils.requireCurrentUser().id(), request)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MENTOR')")
    public ApiResponse<MentorResponse> me() {
        return ApiResponse.ok(mentorService.me(SecurityUtils.requireCurrentUser().id()));
    }

    /** The mentor's own posts in every state, newest change first. */
    @GetMapping("/povs")
    @PreAuthorize("hasRole('MENTOR')")
    public ApiResponse<PageResponse<MyPovResponse>> myPosts(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(povService.mine(SecurityUtils.requireCurrentUser().id(),
                PageRequests.of(page, size, Sort.unsorted())));
    }
}
