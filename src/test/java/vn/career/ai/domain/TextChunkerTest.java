package vn.career.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private static String sentences(int count, int lengthEach) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append("Câu số ").append(i).append(' ').append("a".repeat(Math.max(0, lengthEach - 12))).append(". ");
        }
        return text.toString().trim();
    }

    @Test
    void shortTextIsOneChunk() {
        List<String> chunks = new TextChunker(500, 50).chunk("Một câu ngắn. Và một câu nữa.");

        assertThat(chunks).containsExactly("Một câu ngắn. Và một câu nữa.");
    }

    @Test
    void blankTextGivesNoChunks() {
        assertThat(new TextChunker(500, 50).chunk("   ")).isEmpty();
        assertThat(new TextChunker(500, 50).chunk(null)).isEmpty();
        assertThat(new TextChunker(500, 50).chunk("")).isEmpty();
    }

    @Test
    void longTextIsSplitIntoChunksNoBiggerThanTheLimit() {
        TextChunker chunker = new TextChunker(100, 10);   // 400 characters, 40 overlap
        List<String> chunks = chunker.chunk(sentences(40, 100));

        assertThat(chunks.size()).isGreaterThan(5);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(400));
    }

    @Test
    void neighbouringChunksShareTheirBoundarySentences() {
        TextChunker chunker = new TextChunker(100, 30);   // 400 characters, 120 overlap
        List<String> chunks = chunker.chunk(sentences(30, 100));

        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            String firstSentenceOfThis = chunks.get(i).split("(?<=[.!?])\\s+")[0];
            assertThat(previous).as("chunk %d starts with the tail of chunk %d", i, i - 1).contains(firstSentenceOfThis);
        }
    }

    @Test
    void noSentenceIsLost() {
        String text = sentences(25, 90);
        List<String> chunks = new TextChunker(100, 20).chunk(text);

        String joined = String.join(" ", chunks);
        for (int i = 0; i < 25; i++) {
            assertThat(joined).contains("Câu số " + i + " ");
        }
    }

    @Test
    void chunksEndOnSentenceBoundariesWhenSentencesAreSmall() {
        List<String> chunks = new TextChunker(100, 10).chunk(sentences(40, 100));

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).endsWith("."));
    }

    @Test
    void aSingleHugeSentenceIsCutIntoPiecesThatOverlap() {
        String huge = "x".repeat(1000);   // no punctuation at all
        List<String> chunks = new TextChunker(50, 10).chunk(huge);   // 200 characters, 40 overlap

        assertThat(chunks.size()).isGreaterThan(4);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(200));
        assertThat(chunks.stream().mapToInt(String::length).sum()).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void paragraphBreaksAreBoundaries() {
        List<String> chunks = new TextChunker(500, 50).chunk("Đoạn một\n\nĐoạn hai\nĐoạn ba");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("Đoạn một Đoạn hai Đoạn ba");
    }

    @Test
    void anOverlapLargerThanAChunkStillTerminates() {
        // overlap is capped at half a chunk, so this must finish
        List<String> chunks = new TextChunker(60, 500).chunk(sentences(30, 90));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isLessThan(200);
    }

    @Test
    void defaultsGiveAboutTwoThousandCharacterChunks() {
        List<String> chunks = new TextChunker(500, 50).chunk(sentences(60, 100));   // 6000 characters

        assertThat(chunks.size()).isBetween(3, 5);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(2000));
    }
}
