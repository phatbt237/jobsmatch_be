package vn.career.ai.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.career.ai.api.dto.ChatRequestBody;
import vn.career.ai.api.dto.ChatResponse;
import vn.career.ai.api.dto.SourceRef;
import vn.career.ai.domain.ChatMessage;
import vn.career.ai.domain.CrisisDetector;
import vn.career.ai.infrastructure.ChatMessageRepository;
import vn.career.ai.infrastructure.ChatRateLimiter;
import vn.career.ai.infrastructure.VectorStore;
import vn.career.auth.application.AccountAccessApi;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.common.exception.AppException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.ForbiddenException;
import vn.career.common.exception.NotFoundException;
import vn.career.common.security.AuthenticatedUser;

/**
 * RAG chatbot. For each message: consent check, hourly rate limit, safety check for distress, retrieval of the most
 * similar chunks (optionally limited to one major), then the LLM answers from that context and the last 10 messages.
 * Message text is personal data and is never logged.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final AccountAccessApi accountAccess;
    private final CatalogQueryApi catalog;
    private final ChatRateLimiter rateLimiter;
    private final LlmGateway gateway;
    private final VectorStore vectorStore;
    private final ChatMessageRepository messages;
    private final AiProperties properties;
    private final String systemPrompt = loadSystemPrompt();

    /**
     * Deliberately not @Transactional: a database connection must not be held while waiting for the LLM.
     * The two messages are saved together at the end, in one transaction of their own.
     */
    public ChatResponse chat(AuthenticatedUser caller, ChatRequestBody request) {
        UUID userId = caller.id();
        if (!accountAccess.canUseAiFeatures(userId)) {
            throw new ForbiddenException(ErrorCode.CONSENT_REQUIRED,
                    "A parent has to approve your account before you can use the chatbot");
        }
        String message = request.message().trim();
        if (message.length() > properties.chat().maxMessageLength()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Message is too long");
        }
        if (request.majorId() != null && !catalog.majorExists(request.majorId())) {
            throw new NotFoundException(ErrorCode.MAJOR_NOT_FOUND, "Major not found");
        }
        UUID conversationId = resolveConversation(userId, request.conversationId());
        rateLimiter.countOrReject(userId);

        if (CrisisDetector.isCrisis(message)) {
            return reply(userId, conversationId, message, CrisisDetector.SUPPORT_MESSAGE, List.of());
        }

        List<LlmGateway.ChatTurn> history = history(userId, conversationId);
        List<VectorStore.Hit> hits = retrieve(message, request.majorId());
        List<LlmGateway.ContextChunk> context = hits.stream()
                .map(h -> new LlmGateway.ContextChunk(h.sourceType(), h.sourceId(),
                        String.valueOf(h.metadata().getOrDefault("title", "")), h.text(), h.metadata()))
                .toList();

        LlmGateway.ChatAnswer answer;
        try {
            answer = gateway.chat(new LlmGateway.ChatRequest(systemPrompt, history, context, message));
        } catch (LlmGateway.LlmException e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "The assistant is busy right now, please try again in a moment");
        }
        List<SourceRef> sources = answer.usedContext() ? sourcesOf(context) : List.of();
        return reply(userId, conversationId, message, answer.text(), sources);
    }

    // ---------------------------------------------------------------- steps

    private UUID resolveConversation(UUID userId, UUID requested) {
        if (requested == null) {
            return UUID.randomUUID();
        }
        // Someone else's conversation looks exactly like one that does not exist
        if (!messages.existsByConversationIdAndUserId(requested, userId)) {
            throw new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found");
        }
        return requested;
    }

    /** The last N messages of the conversation, oldest first. */
    private List<LlmGateway.ChatTurn> history(UUID userId, UUID conversationId) {
        List<ChatMessage> recent = new ArrayList<>(messages.findByConversationIdAndUserIdOrderBySeqDesc(
                conversationId, userId, PageRequest.of(0, properties.chat().historySize())));
        Collections.reverse(recent);
        return recent.stream().map(m -> new LlmGateway.ChatTurn(m.getRole().name(), m.getContent())).toList();
    }

    private List<VectorStore.Hit> retrieve(String message, UUID majorId) {
        try {
            return vectorStore.search(gateway.embed(message), properties.rag().topK(), majorId, properties.rag().minSimilarity());
        } catch (LlmGateway.LlmException e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "The assistant is busy right now, please try again in a moment");
        }
    }

    private ChatResponse reply(UUID userId, UUID conversationId, String userMessage, String answer, List<SourceRef> sources) {
        List<Map<String, Object>> stored = sources.stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", s.type());
            map.put("id", s.id().toString());
            map.put("title", s.title());
            return map;
        }).toList();
        messages.saveAll(List.of(
                ChatMessage.of(userId, conversationId, ChatMessage.Role.USER, userMessage, null),
                ChatMessage.of(userId, conversationId, ChatMessage.Role.ASSISTANT, answer, stored)));
        return new ChatResponse(conversationId, answer, sources);
    }

    /** Distinct (type, id) pairs in the order of relevance. */
    private static List<SourceRef> sourcesOf(List<LlmGateway.ContextChunk> context) {
        Map<String, SourceRef> unique = new LinkedHashMap<>();
        for (LlmGateway.ContextChunk chunk : context) {
            unique.putIfAbsent(chunk.sourceType() + ":" + chunk.sourceId(),
                    new SourceRef(chunk.sourceType(), chunk.sourceId(), chunk.title()));
        }
        return List.copyOf(unique.values());
    }

    private static String loadSystemPrompt() {
        try (var in = new ClassPathResource("prompts/chat-system.st").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompts/chat-system.st", e);
        }
    }
}
