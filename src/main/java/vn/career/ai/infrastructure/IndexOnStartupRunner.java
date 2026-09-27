package vn.career.ai.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vn.career.ai.application.AiProperties;
import vn.career.ai.application.ContentIndexer;

/** Builds the search index at startup when it is empty and {@code app.ai.rag.index-on-startup} is true. */
@Component
@RequiredArgsConstructor
@Slf4j
class IndexOnStartupRunner implements ApplicationRunner {

    private final AiProperties properties;
    private final VectorStore store;
    private final ContentIndexer indexer;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.rag().indexOnStartup() || store.count() > 0) {
            return;
        }
        try {
            indexer.rebuildAll();
        } catch (RuntimeException e) {
            log.warn("Initial indexing failed ({}), use POST /api/v1/admin/embeddings/rebuild", e.getClass().getSimpleName());
        }
    }
}
