package vn.career.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code majorId} limits the answer to one major. {@code conversationId} continues an earlier conversation
 * (leave it out to start a new one).
 */
public record ChatRequestBody(
        @NotBlank @Size(max = 2000) String message,
        UUID majorId,
        UUID conversationId) {
}
