package vn.career.ai.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into overlapping chunks for embedding. Sizes are in characters (about 4 per token): the default of
 * 500 tokens is 2000 characters with 200 characters (50 tokens) shared between neighbouring chunks.
 * Chunks end at sentence or paragraph boundaries when possible. Pure logic.
 */
public final class TextChunker {

    private static final int CHARS_PER_TOKEN = 4;

    private final int maxChars;
    private final int overlapChars;

    public TextChunker(int chunkTokens, int overlapTokens) {
        this.maxChars = Math.max(50, chunkTokens * CHARS_PER_TOKEN);
        // the overlap must be clearly smaller than a chunk or chunking would never advance
        this.overlapChars = Math.min(Math.max(0, overlapTokens * CHARS_PER_TOKEN), maxChars / 2);
    }

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        List<String> pieces = sentences(text);
        List<String> current = new ArrayList<>();
        int currentLength = 0;

        for (String piece : pieces) {
            if (currentLength > 0 && currentLength + 1 + piece.length() > maxChars) {
                chunks.add(String.join(" ", current));
                current = overlapTail(current);
                currentLength = length(current);
            }
            current.add(piece);
            currentLength = length(current);
        }
        if (!current.isEmpty()) {
            String last = String.join(" ", current);
            // do not emit a final chunk that only repeats the overlap of the previous one
            if (chunks.isEmpty() || !chunks.get(chunks.size() - 1).endsWith(last)) {
                chunks.add(last);
            }
        }
        return chunks;
    }

    /** Sentences, with any sentence longer than a chunk cut into pieces first. */
    private List<String> sentences(String text) {
        List<String> result = new ArrayList<>();
        for (String sentence : text.trim().split("(?<=[.!?…])\\s+|\\n+")) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int step = Math.max(1, maxChars - overlapChars);
            if (trimmed.length() <= maxChars) {
                result.add(trimmed);
            } else {
                for (int start = 0; start < trimmed.length(); start += step) {
                    result.add(trimmed.substring(start, Math.min(trimmed.length(), start + maxChars)));
                    if (start + maxChars >= trimmed.length()) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    /** The last sentences of a finished chunk that fit in the overlap budget. */
    private List<String> overlapTail(List<String> finished) {
        List<String> tail = new ArrayList<>();
        int total = 0;
        for (int i = finished.size() - 1; i >= 0; i--) {
            int add = finished.get(i).length() + (tail.isEmpty() ? 0 : 1);
            if (total + add > overlapChars) {
                break;
            }
            tail.add(0, finished.get(i));
            total += add;
        }
        return tail;
    }

    private static int length(List<String> pieces) {
        return pieces.stream().mapToInt(String::length).sum() + Math.max(0, pieces.size() - 1);
    }
}
