package vn.career.ai.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.career.ai.application.AiProperties;
import vn.career.ai.application.LlmGateway;

/**
 * Real model through Spring AI. Only created when {@code app.ai.enabled=true}; that also needs a provider
 * ({@code spring.ai.model.chat} and {@code spring.ai.model.embedding}) and its API key, see application.yml.
 * Provider errors are reduced to {@link LlmException} without content so nothing personal ends up in logs.
 */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
class SpringAiLlmGateway implements LlmGateway {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final AiProperties properties;
    private final String modelName;

    SpringAiLlmGateway(ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel, AiProperties properties,
                       @Value("${spring.ai.openai.chat.options.model:unknown}") String modelName) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.modelName = modelName;
    }

    @Override
    public LlmResponse generateExplanation(ExplanationRequest request) {
        try {
            String content = chatClient.prompt().user(request.prompt()).call().content();
            return new LlmResponse(content == null ? "" : content, modelName);
        } catch (RuntimeException e) {
            throw new LlmException("LLM explanation call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public ChatAnswer chat(ChatRequest request) {
        try {
            List<Message> history = new ArrayList<>();
            for (ChatTurn turn : request.history()) {
                history.add("ASSISTANT".equals(turn.role()) ? new AssistantMessage(turn.content()) : new UserMessage(turn.content()));
            }
            String content = chatClient.prompt()
                    .system(request.systemPrompt() + "\n\n" + contextBlock(request.context()))
                    .messages(history)
                    .user(request.userMessage())
                    .call()
                    .content();
            // A real model decides for itself whether the context helped; sources are shown whenever it was supplied.
            return new ChatAnswer(content == null ? "" : content, modelName, !request.context().isEmpty());
        } catch (RuntimeException e) {
            throw new LlmException("LLM chat call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private static String contextBlock(List<ContextChunk> context) {
        if (context.isEmpty()) {
            return "Ngữ cảnh: (không có dữ liệu liên quan)";
        }
        return "Ngữ cảnh:\n" + context.stream()
                .map(c -> "[" + c.sourceType() + ": " + c.title() + describe(c) + "]\n" + c.text())
                .collect(Collectors.joining("\n---\n"));
    }

    private static String describe(ContextChunk chunk) {
        Object job = chunk.metadata().get("jobTitle");
        Object years = chunk.metadata().get("yearsExperience");
        return job == null ? "" : " - chia sẻ của một " + job + (years == null ? "" : " có " + years + " năm kinh nghiệm");
    }

    @Override
    public float[] embed(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (RuntimeException e) {
            throw new LlmException("Embedding call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public int embeddingDimensions() {
        return properties.embeddingDimensions();
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
