package vn.career.ai.api.dto;

import java.util.List;
import java.util.UUID;

/** {@code sources} is empty when the bot had nothing to base the answer on or declined to answer. */
public record ChatResponse(UUID conversationId, String answer, List<SourceRef> sources) {
}
