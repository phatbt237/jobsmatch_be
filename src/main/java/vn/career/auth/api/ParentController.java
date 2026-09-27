package vn.career.auth.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.career.auth.api.dto.InviteResponse;
import vn.career.auth.api.dto.LinkParentRequest;
import vn.career.auth.api.dto.LinkedStudentResponse;
import vn.career.auth.application.ParentLinkService;
import vn.career.common.api.ApiResponse;
import vn.career.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1/parent")
@RequiredArgsConstructor
@Tag(name = "Parent link")
public class ParentController {

    private final ParentLinkService parentLinkService;

    @PostMapping("/invites")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<InviteResponse> createInvite() {
        return ApiResponse.ok(parentLinkService.createInvite(SecurityUtils.requireCurrentUser().id()));
    }

    @PostMapping("/links")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<LinkedStudentResponse> link(@Valid @RequestBody LinkParentRequest request) {
        return ApiResponse.ok(
                parentLinkService.acceptInvite(SecurityUtils.requireCurrentUser().id(), request.inviteCode()));
    }

    @GetMapping("/students")
    @PreAuthorize("hasRole('PARENT')")
    public ApiResponse<List<LinkedStudentResponse>> students() {
        return ApiResponse.ok(parentLinkService.listStudents(SecurityUtils.requireCurrentUser().id()));
    }
}
