package vn.career.content.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.career.common.api.ApiResponse;
import vn.career.common.api.PageRequests;
import vn.career.common.api.PageResponse;
import vn.career.common.security.SecurityUtils;
import vn.career.content.api.dto.QaAnswerRequest;
import vn.career.content.api.dto.QaAnswerResponse;
import vn.career.content.api.dto.QaThreadRequest;
import vn.career.content.api.dto.QaThreadResponse;
import vn.career.content.application.QaService;

/** Community Q&A. Needs login; students also need any required parental consent (else 403 CONSENT_REQUIRED). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Q&A")
public class QaController {

    private final QaService qaService;

    @GetMapping("/majors/{majorId}/qa")
    public ApiResponse<PageResponse<QaThreadResponse>> threads(@PathVariable UUID majorId,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(qaService.listThreads(SecurityUtils.requireCurrentUser(), majorId,
                PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/majors/{majorId}/qa")
    public ResponseEntity<ApiResponse<QaThreadResponse>> ask(@PathVariable UUID majorId,
                                                             @Valid @RequestBody QaThreadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(qaService.ask(SecurityUtils.requireCurrentUser(), majorId, request)));
    }

    @GetMapping("/qa/{threadId}/answers")
    public ApiResponse<PageResponse<QaAnswerResponse>> answers(@PathVariable UUID threadId,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(qaService.listAnswers(SecurityUtils.requireCurrentUser(), threadId,
                PageRequests.of(page, size, Sort.unsorted())));
    }

    /** Verified mentors' answers are flagged {@code mentorAnswer}. */
    @PostMapping("/qa/{threadId}/answers")
    public ResponseEntity<ApiResponse<QaAnswerResponse>> answer(@PathVariable UUID threadId,
                                                                @Valid @RequestBody QaAnswerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(qaService.answer(SecurityUtils.requireCurrentUser(), threadId, request)));
    }
}
