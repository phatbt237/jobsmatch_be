package vn.career.ai.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.career.ai.application.ContentIndexer;
import vn.career.common.api.ApiResponse;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;

@RestController
@RequestMapping("/api/v1/admin/embeddings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - embeddings")
public class AdminEmbeddingController {

    private final ContentIndexer indexer;
    private final AuditService auditService;

    /**
     * Rebuilds the chatbot's search index from all active majors and published posts. Runs to completion before
     * answering (it can take a while with a real embedding model) and reports what it did.
     */
    @PostMapping("/rebuild")
    public ApiResponse<ContentIndexer.RebuildResult> rebuild() {
        ContentIndexer.RebuildResult result = indexer.rebuildAll();
        auditService.record(AuditAction.EMBEDDINGS_REBUILT, "SearchIndex", null,
                Map.of("majors", result.majors(), "povs", result.povs(), "chunks", result.chunks(), "failed", result.failed()));
        return ApiResponse.ok(result);
    }
}
