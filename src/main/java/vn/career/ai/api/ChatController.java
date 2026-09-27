package vn.career.ai.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.career.ai.api.dto.ChatRequestBody;
import vn.career.ai.api.dto.ChatResponse;
import vn.career.ai.application.ChatService;
import vn.career.common.api.ApiResponse;
import vn.career.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Chatbot")
public class ChatController {

    private final ChatService chatService;

    /**
     * Career guidance chatbot. Answers only from the knowledge base (majors and mentors' posts) and lists its
     * {@code sources}. Students need any required parental consent (else 403 CONSENT_REQUIRED). At most 20 messages
     * per hour (else 429 with Retry-After).
     */
    @PostMapping("/chat")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequestBody request) {
        return ApiResponse.ok(chatService.chat(SecurityUtils.requireCurrentUser(), request));
    }
}
