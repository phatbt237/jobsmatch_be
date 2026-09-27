package vn.career.auth.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.career.auth.api.dto.UserResponse;
import vn.career.auth.application.AccountDeletionService;
import vn.career.auth.application.AuthService;
import vn.career.auth.application.PersonalDataService;
import vn.career.common.api.ApiResponse;
import vn.career.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Account")
public class MeController {

    private final AuthService authService;
    private final PersonalDataService personalDataService;
    private final AccountDeletionService accountDeletionService;

    @GetMapping
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(authService.me(SecurityUtils.requireCurrentUser().id()));
    }

    /**
     * Downloads everything stored about the caller as JSON (account, survey answers and results, chat history,
     * posts, Q&A). Audited.
     */
    @GetMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> export() {
        Map<String, Object> data = personalDataService.export(SecurityUtils.requireCurrentUser().id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("my-data.json").build().toString())
                .body(ApiResponse.ok(data));
    }

    /**
     * Permanently deletes the caller's data and anonymises the account. Cannot be undone; clients should ask the
     * user to confirm first. Admin accounts cannot be deleted. Audited.
     */
    @DeleteMapping
    public ApiResponse<Void> deleteAccount() {
        accountDeletionService.deleteAccount(SecurityUtils.requireCurrentUser().id());
        return ApiResponse.ok(null);
    }
}
