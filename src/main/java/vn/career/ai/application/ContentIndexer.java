package vn.career.ai.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.career.ai.domain.TextChunker;
import vn.career.ai.infrastructure.VectorStore;
import vn.career.catalog.application.CatalogQueryApi;
import vn.career.content.application.PovSourceApi;

/**
 * Keeps the search index in step with the content: splits text into chunks (about 500 tokens, 50 overlap), embeds
 * them and stores them, always deleting the old chunks of the same source first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentIndexer {

    static final String POV = "POV";
    static final String MAJOR = "MAJOR";

    public record RebuildResult(int majors, int povs, int chunks, int failed, int removedChunks) {
    }

    private final PovSourceApi povSource;
    private final CatalogQueryApi catalog;
    private final LlmGateway gateway;
    private final VectorStore store;
    private final AiProperties properties;

    /** Indexes a published post. If the post is not published (any more) its chunks are removed. Returns the chunk count. */
    public int indexPov(UUID povId) {
        Optional<PovSourceApi.PovDocument> document = povSource.loadPublished(povId);
        if (document.isEmpty()) {
            store.deleteBySource(POV, povId);
            return 0;
        }
        PovSourceApi.PovDocument doc = document.get();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("majorId", doc.majorId().toString());
        metadata.put("title", doc.title());
        metadata.put("jobTitle", doc.jobTitle());
        metadata.put("yearsExperience", doc.yearsExperience());
        return embedAndStore(POV, povId, doc.title(), doc.text(), metadata);
    }

    /** Indexes a major. If the major is inactive or gone its chunks are removed. */
    public int indexMajor(UUID majorId) {
        Optional<CatalogQueryApi.MajorDocument> document = catalog.majorDocument(majorId);
        if (document.isEmpty()) {
            store.deleteBySource(MAJOR, majorId);
            return 0;
        }
        CatalogQueryApi.MajorDocument doc = document.get();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("majorId", majorId.toString());
        metadata.put("title", doc.name());
        return embedAndStore(MAJOR, majorId, doc.name(), doc.text(), metadata);
    }

    /**
     * Rebuilds the whole index: every active major and every published post, then drops chunks of sources that no
     * longer exist. A source that fails to embed is counted in {@code failed} and the rest continues.
     */
    public RebuildResult rebuildAll() {
        List<UUID> majorIds = catalog.activeMajorIds();
        List<UUID> povIds = povSource.publishedPovIds();
        int chunks = 0;
        int failed = 0;
        int majorsDone = 0;
        int povsDone = 0;
        for (UUID id : majorIds) {
            try {
                chunks += indexMajor(id);
                majorsDone++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Indexing major {} failed ({})", id, e.getClass().getSimpleName());
            }
        }
        for (UUID id : povIds) {
            try {
                chunks += indexPov(id);
                povsDone++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Indexing post {} failed ({})", id, e.getClass().getSimpleName());
            }
        }
        int removed = 0;
        if (failed == 0) {
            removed = store.deleteSourcesNotIn(MAJOR, majorIds) + store.deleteSourcesNotIn(POV, povIds);
        }
        log.info("Search index rebuilt: {} majors, {} posts, {} chunks, {} failed, {} stale chunks removed",
                majorsDone, povsDone, chunks, failed, removed);
        return new RebuildResult(majorsDone, povsDone, chunks, failed, removed);
    }

    private int embedAndStore(String type, UUID id, String title, String text, Map<String, Object> metadata) {
        TextChunker chunker = new TextChunker(properties.rag().chunkTokens(), properties.rag().overlapTokens());
        List<VectorStore.Chunk> chunks = new ArrayList<>();
        for (String chunk : chunker.chunk(text)) {
            // the title is part of what is embedded so short chunks still say what they are about
            chunks.add(new VectorStore.Chunk(chunk, gateway.embed(title + "\n" + chunk), metadata));
        }
        store.replaceChunks(type, id, chunks);
        return chunks.size();
    }
}
