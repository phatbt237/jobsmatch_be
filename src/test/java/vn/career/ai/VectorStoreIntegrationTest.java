package vn.career.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import vn.career.ai.infrastructure.VectorStore;
import vn.career.support.AbstractIntegrationTest;

/** The pgvector SQL against a real database: cosine ordering, filters, replacement and cleanup. */
class VectorStoreIntegrationTest extends AbstractIntegrationTest {

    private static final int DIMENSIONS = 1536;

    @Autowired
    private VectorStore store;

    private static float[] direction(int... weightedIndexes) {
        float[] vector = new float[DIMENSIONS];
        for (int index : weightedIndexes) {
            vector[index] = 1f;
        }
        double norm = Math.sqrt(weightedIndexes.length);
        for (int index : weightedIndexes) {
            vector[index] = (float) (1 / norm);
        }
        return vector;
    }

    private static VectorStore.Chunk chunk(String text, float[] embedding, UUID majorId) {
        return new VectorStore.Chunk(text, embedding, Map.of("majorId", majorId.toString(), "title", "Tiêu đề " + text,
                "jobTitle", "Kỹ sư", "yearsExperience", 5));
    }

    @Test
    void searchReturnsTheClosestChunksFirstWithTheirCosineSimilarity() {
        UUID major = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        try {
            store.replaceChunks("POV", source, List.of(
                    chunk("giống hệt", direction(10), major),
                    chunk("gần giống", direction(10, 11), major),
                    chunk("khác hẳn", direction(500), major)));

            List<VectorStore.Hit> hits = store.search(direction(10), 5, major, 0.0);

            assertThat(hits).extracting(VectorStore.Hit::text).containsExactly("giống hệt", "gần giống", "khác hẳn");
            assertThat(hits.get(0).similarity()).isBetween(0.999, 1.001);
            assertThat(hits.get(1).similarity()).isBetween(0.70, 0.72);   // cos 45 degrees
            assertThat(hits.get(2).similarity()).isBetween(-0.001, 0.001);
            assertThat(hits.get(0).sourceType()).isEqualTo("POV");
            assertThat(hits.get(0).sourceId()).isEqualTo(source);
            assertThat(hits.get(0).chunkIndex()).isZero();
        } finally {
            store.deleteBySource("POV", source);
        }
    }

    @Test
    void theLimitAndTheMinimumSimilarityAreApplied() {
        UUID major = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        try {
            store.replaceChunks("POV", source, List.of(chunk("a", direction(20), major), chunk("b", direction(20, 21), major),
                    chunk("c", direction(22), major)));

            assertThat(store.search(direction(20), 1, major, 0.0)).extracting(VectorStore.Hit::text).containsExactly("a");
            assertThat(store.search(direction(20), 5, major, 0.5)).extracting(VectorStore.Hit::text).containsExactly("a", "b");
            assertThat(store.search(direction(20), 5, major, 0.99)).extracting(VectorStore.Hit::text).containsExactly("a");
        } finally {
            store.deleteBySource("POV", source);
        }
    }

    @Test
    void theMajorFilterOnlyReturnsChunksAboutThatMajor() {
        UUID majorA = UUID.randomUUID();
        UUID majorB = UUID.randomUUID();
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        try {
            store.replaceChunks("POV", sourceA, List.of(chunk("của A", direction(30), majorA)));
            store.replaceChunks("MAJOR", sourceB, List.of(chunk("của B", direction(30), majorB)));

            assertThat(store.search(direction(30), 5, majorA, 0.0)).extracting(VectorStore.Hit::text).containsExactly("của A");
            assertThat(store.search(direction(30), 5, majorB, 0.0)).extracting(VectorStore.Hit::text).containsExactly("của B");
            assertThat(store.search(direction(30), 5, UUID.randomUUID(), 0.0)).isEmpty();
            // without a filter both are candidates
            assertThat(store.search(direction(30), 50, null, 0.99)).extracting(VectorStore.Hit::text).contains("của A", "của B");
        } finally {
            store.deleteBySource("POV", sourceA);
            store.deleteBySource("MAJOR", sourceB);
        }
    }

    @Test
    void metadataSurvivesTheRoundTrip() {
        UUID major = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        try {
            store.replaceChunks("POV", source, List.of(chunk("x", direction(40), major)));

            VectorStore.Hit hit = store.search(direction(40), 1, major, 0.0).get(0);

            assertThat(hit.metadata()).containsEntry("majorId", major.toString()).containsEntry("jobTitle", "Kỹ sư")
                    .containsEntry("yearsExperience", 5).containsEntry("title", "Tiêu đề x");
        } finally {
            store.deleteBySource("POV", source);
        }
    }

    @Test
    void replacingASourceRemovesItsOldChunks() {
        UUID major = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        try {
            store.replaceChunks("POV", source, List.of(chunk("cũ 1", direction(50), major), chunk("cũ 2", direction(51), major),
                    chunk("cũ 3", direction(52), major)));
            assertThat(store.countBySource("POV", source)).isEqualTo(3);

            store.replaceChunks("POV", source, List.of(chunk("mới", direction(53), major)));

            assertThat(store.countBySource("POV", source)).isEqualTo(1);
            assertThat(store.search(direction(50), 5, major, 0.5)).isEmpty();
            assertThat(store.search(direction(53), 5, major, 0.5)).extracting(VectorStore.Hit::text).containsExactly("mới");
            // replacing with nothing clears the source
            store.replaceChunks("POV", source, List.of());
            assertThat(store.countBySource("POV", source)).isZero();
        } finally {
            store.deleteBySource("POV", source);
        }
    }

    @Test
    void chunksOfSourcesThatNoLongerExistAreRemoved() {
        UUID major = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        UUID otherType = UUID.randomUUID();
        try {
            store.replaceChunks("POV", kept, List.of(chunk("giữ", direction(60), major)));
            store.replaceChunks("POV", gone, List.of(chunk("bỏ", direction(61), major)));
            store.replaceChunks("MAJOR", otherType, List.of(chunk("khác loại", direction(62), major)));

            int removed = store.deleteSourcesNotIn("POV", List.of(kept, UUID.randomUUID()));

            assertThat(removed).isGreaterThanOrEqualTo(1);
            assertThat(store.countBySource("POV", gone)).isZero();
            assertThat(store.countBySource("POV", kept)).isEqualTo(1);
            assertThat(store.countBySource("MAJOR", otherType)).as("other source types are untouched").isEqualTo(1);
        } finally {
            store.deleteBySource("POV", kept);
            store.deleteBySource("POV", gone);
            store.deleteBySource("MAJOR", otherType);
        }
    }

    @Test
    void aVectorOfTheWrongSizeIsRejectedByTheDatabase() {
        UUID source = UUID.randomUUID();

        assertThatThrownBy(() -> store.replaceChunks("POV", source,
                List.of(new VectorStore.Chunk("x", new float[] {1f, 0f, 0f}, Map.of()))))
                .isInstanceOf(DataAccessException.class);
        assertThat(store.countBySource("POV", source)).as("the failed replacement rolled back").isZero();
    }

    @Test
    void invalidSourceTypesAreRejected() {
        assertThatThrownBy(() -> store.replaceChunks("BLOG", UUID.randomUUID(),
                List.of(new VectorStore.Chunk("x", direction(1), Map.of())))).isInstanceOf(DataAccessException.class);
    }
}
