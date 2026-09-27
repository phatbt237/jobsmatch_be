package vn.career.ai.infrastructure;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.career.ai.application.ContentIndexer;
import vn.career.catalog.application.MajorChangedEvent;
import vn.career.content.application.PovPublishedEvent;
import vn.career.content.application.PovRemovedEvent;

/** Re-indexes content after the change that triggered it has been committed, on the AI thread pool. */
@Component
@Slf4j
class ContentIndexListener {

    private final Executor executor;
    private final ContentIndexer indexer;

    ContentIndexListener(@Qualifier(AiExecutorConfig.EXECUTOR) Executor executor, ContentIndexer indexer) {
        this.executor = executor;
        this.indexer = indexer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPovPublished(PovPublishedEvent event) {
        run("post", event.povId(), indexer::indexPov);
    }

    /** The post no longer exists, so indexing it simply removes its chunks. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPovRemoved(PovRemovedEvent event) {
        run("post", event.povId(), indexer::indexPov);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMajorChanged(MajorChangedEvent event) {
        run("major", event.majorId(), indexer::indexMajor);
    }

    private void run(String what, UUID id, Consumer<UUID> action) {
        try {
            executor.execute(() -> {
                try {
                    action.accept(id);
                } catch (RuntimeException e) {
                    // the admin can always run a full rebuild
                    log.warn("Indexing {} {} failed ({})", what, id, e.getClass().getSimpleName());
                }
            });
        } catch (TaskRejectedException e) {
            log.warn("AI queue is full, indexing of {} {} skipped", what, id);
        }
    }
}
