package vn.career.ai.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.career.recommendation.application.ExplanationApi;

/**
 * The only door to a language model. Everything else in the app talks to this interface, so the provider can be
 * swapped, and tests and development without an API key use {@code FakeLlmGateway}.
 * The LLM only ever writes text: it never decides which major fits a student.
 */
public interface LlmGateway {

    /** Writes the explanation JSON for {@code request.prompt()}. May throw {@link LlmException}. */
    LlmResponse generateExplanation(ExplanationRequest request);

    /** Answers a chat message using only the given context. May throw {@link LlmException}. */
    ChatAnswer chat(ChatRequest request);

    /** Embedding vector of the text, with {@link #embeddingDimensions()} entries. */
    float[] embed(String text);

    int embeddingDimensions();

    /** Provider or model name stored with generated texts. */
    String modelName();

    /**
     * {@code prompt} is what a real model receives. {@code input} carries the same facts in structured form
     * (used by the fake gateway, ignored by real models).
     */
    record ExplanationRequest(String prompt, ExplanationApi.ExplanationInput input) {
    }

    record LlmResponse(String text, String model) {
    }

    /** {@code usedContext} is false when the answer is a refusal or "no data", so no sources should be shown for it. */
    record ChatAnswer(String text, String model, boolean usedContext) {
    }

    /** {@code role} is USER or ASSISTANT. */
    record ChatTurn(String role, String content) {
    }

    /** A passage retrieved from the knowledge base. {@code metadata} may hold jobTitle, yearsExperience, majorId... */
    record ContextChunk(String sourceType, UUID sourceId, String title, String text, Map<String, Object> metadata) {
    }

    record ChatRequest(String systemPrompt, List<ChatTurn> history, List<ContextChunk> context, String userMessage) {
    }

    /** The model could not be reached or refused. Never contains user content. */
    class LlmException extends RuntimeException {
        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }

        public LlmException(String message) {
            super(message);
        }
    }
}
