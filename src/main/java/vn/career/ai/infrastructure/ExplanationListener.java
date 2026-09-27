package vn.career.ai.infrastructure;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.career.ai.application.ExplanationService;
import vn.career.recommendation.application.AttemptScoredEvent;
import vn.career.recommendation.application.ExplanationApi;

/**
 * Starts the explanation once the submit transaction has committed. The work goes to the AI thread pool, so the
 * submit request has already returned by the time the LLM is called.
 */
@Component
@Slf4j
class ExplanationListener {

    private final Executor executor;
    private final ExplanationService explanationService;
    private final ExplanationApi explanationApi;

    ExplanationListener(@Qualifier(AiExecutorConfig.EXECUTOR) Executor executor,
                        ExplanationService explanationService, ExplanationApi explanationApi) {
        this.executor = executor;
        this.explanationService = explanationService;
        this.explanationApi = explanationApi;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAttemptScored(AttemptScoredEvent event) {
        try {
            executor.execute(() -> {
                try {
                    explanationService.explain(event.attemptId());
                } catch (RuntimeException e) {
                    log.error("Explanation task for attempt {} crashed ({})", event.attemptId(), e.getClass().getSimpleName());
                    explanationApi.markFailed(event.attemptId());
                }
            });
        } catch (TaskRejectedException e) {
            log.error("AI queue is full, explanation for attempt {} skipped", event.attemptId());
            explanationApi.markFailed(event.attemptId());
        }
    }
}
