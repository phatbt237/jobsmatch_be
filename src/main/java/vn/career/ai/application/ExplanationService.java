package vn.career.ai.application;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.career.recommendation.application.ExplanationApi;

/**
 * Asks the LLM to explain the recommendations of one attempt, with retries. Runs on the AI thread pool, never on a
 * request thread. When every attempt fails the explanations are marked FAILED; the recommendations stay valid.
 * Only ids, counts and exception class names are logged, never scores, answers or model replies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExplanationService {

    private final ExplanationApi explanationApi;
    private final ExplanationPromptBuilder promptBuilder;
    private final ExplanationParser parser;
    private final LlmGateway gateway;
    private final AiProperties properties;

    public void explain(UUID attemptId) {
        ExplanationApi.ExplanationInput input = explanationApi.loadInput(attemptId);
        if (input.majors().isEmpty()) {
            return;
        }
        Set<UUID> expected = input.majors().stream().map(ExplanationApi.MajorLine::majorId).collect(Collectors.toSet());
        String prompt = promptBuilder.build(input);
        int maxAttempts = Math.max(1, properties.explanation().maxAttempts());
        Duration backoff = properties.explanation().backoff();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                LlmGateway.LlmResponse response = gateway.generateExplanation(new LlmGateway.ExplanationRequest(prompt, input));
                ExplanationParser.ParsedExplanation parsed = parser.parse(response.text(), expected);
                explanationApi.saveExplanation(attemptId, parsed.summary(), parsed.textsByMajorId(), response.model());
                log.info("Explanation for attempt {} written on try {}", attemptId, attempt);
                return;
            } catch (RuntimeException e) {
                log.warn("Explanation for attempt {} failed on try {} of {} ({})", attemptId, attempt, maxAttempts,
                        e.getClass().getSimpleName());
                if (attempt < maxAttempts && !pause(backoff.multipliedBy(1L << (attempt - 1)))) {
                    break;
                }
            }
        }
        explanationApi.markFailed(attemptId);
        log.warn("Explanation for attempt {} marked FAILED", attemptId);
    }

    /** Sleeps between retries. Returns false if the thread was interrupted (shutdown). */
    private static boolean pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
