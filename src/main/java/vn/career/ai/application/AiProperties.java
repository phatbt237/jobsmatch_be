package vn.career.ai.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI settings. {@code enabled=false} (default) uses the fake gateway; set it to true together with a Spring AI
 * provider and API key (see application.yml) to use a real model.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(
        @DefaultValue("false") boolean enabled,
        /** Must match the vector(N) column of content_chunks and the embedding model. */
        @DefaultValue("1536") int embeddingDimensions,
        @DefaultValue Explanation explanation,
        @DefaultValue Chat chat,
        @DefaultValue Rag rag) {

    /** Attempts include the first call: 3 means one try plus two retries. Backoff doubles after every failure. */
    public record Explanation(@DefaultValue("3") int maxAttempts, @DefaultValue("500ms") Duration backoff) {
    }

    public record Chat(
            @DefaultValue("20") int messagesPerHour,
            /** How many earlier messages are sent to the model as context. */
            @DefaultValue("10") int historySize,
            @DefaultValue("2000") int maxMessageLength) {
    }

    public record Rag(
            @DefaultValue("5") int topK,
            /** Rough size of a chunk in tokens (about 4 characters each) and how much neighbouring chunks overlap. */
            @DefaultValue("500") int chunkTokens,
            @DefaultValue("50") int overlapTokens,
            /** Chunks less similar than this (cosine, 0..1) to the question are not shown to the model. 0 keeps all. */
            @DefaultValue("0.0") double minSimilarity,
            /** Build the search index at startup when it is empty (handy in development). */
            @DefaultValue("false") boolean indexOnStartup) {
    }
}
