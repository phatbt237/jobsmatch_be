package vn.career.ai.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chunks of searchable text with their embeddings, stored with pgvector. Uses plain SQL because the vector type has
 * no JPA mapping here. Cosine distance ({@code <=>}) is served by the HNSW index.
 */
@Repository
@RequiredArgsConstructor
public class VectorStore {

    public record Chunk(String text, float[] embedding, Map<String, Object> metadata) {
    }

    public record Hit(String sourceType, UUID sourceId, int chunkIndex, String text, Map<String, Object> metadata,
                      double similarity) {
    }

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** Replaces all chunks of one source (old chunks are removed first) in a single transaction. */
    @Transactional
    public void replaceChunks(String sourceType, UUID sourceId, List<Chunk> chunks) {
        deleteBySource(sourceType, sourceId);
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            jdbc.update("insert into content_chunks (source_type, source_id, chunk_index, text, embedding, metadata) "
                            + "values (?, ?, ?, ?, ?::vector, ?::jsonb)",
                    sourceType, sourceId, i, chunk.text(), toVectorLiteral(chunk.embedding()), toJson(chunk.metadata()));
        }
    }

    @Transactional
    public void deleteBySource(String sourceType, UUID sourceId) {
        jdbc.update("delete from content_chunks where source_type = ? and source_id = ?", sourceType, sourceId);
    }

    /** Removes chunks of sources of this type that are no longer in {@code keepIds}. Returns how many rows went. */
    @Transactional
    public int deleteSourcesNotIn(String sourceType, Collection<UUID> keepIds) {
        if (keepIds.isEmpty()) {
            return jdbc.update("delete from content_chunks where source_type = ?", sourceType);
        }
        String placeholders = String.join(",", keepIds.stream().map(id -> "?").toList());
        Object[] args = new Object[keepIds.size() + 1];
        args[0] = sourceType;
        int i = 1;
        for (UUID id : keepIds) {
            args[i++] = id;
        }
        return jdbc.update("delete from content_chunks where source_type = ? and source_id not in (" + placeholders + ")", args);
    }

    public int count() {
        Integer count = jdbc.queryForObject("select count(*) from content_chunks", Integer.class);
        return count == null ? 0 : count;
    }

    public int countBySource(String sourceType, UUID sourceId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from content_chunks where source_type = ? and source_id = ?", Integer.class, sourceType, sourceId);
        return count == null ? 0 : count;
    }

    /**
     * The {@code limit} chunks closest to the query, best first. With a {@code majorId} only chunks about that major
     * are considered. Hits below {@code minSimilarity} (cosine, 1 = identical) are dropped.
     */
    @Transactional(readOnly = true)
    public List<Hit> search(float[] query, int limit, UUID majorId, double minSimilarity) {
        String vector = toVectorLiteral(query);
        String major = majorId == null ? null : majorId.toString();
        return jdbc.query("select source_type, source_id, chunk_index, text, metadata::text, "
                        + "1 - (embedding <=> ?::vector) as similarity from content_chunks "
                        + "where (cast(? as text) is null or metadata ->> 'majorId' = cast(? as text)) "
                        + "and 1 - (embedding <=> ?::vector) >= ? "
                        + "order by embedding <=> ?::vector limit ?",
                (rs, rowNum) -> new Hit(rs.getString(1), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4),
                        fromJson(rs.getString(5)), rs.getDouble(6)),
                vector, major, major, vector, minSimilarity, vector, limit);
    }

    static String toVectorLiteral(float[] values) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(String.format(Locale.ROOT, "%.7f", values[i]));
        }
        return literal.append(']').toString();
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise chunk metadata", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
