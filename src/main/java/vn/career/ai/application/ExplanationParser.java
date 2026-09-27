package vn.career.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates the JSON an LLM returned: {@code {"summary": "...", "majors": [{"majorId": "...", "text": "..."}]}}.
 * Anything that does not parse, lacks a summary, or misses a requested major is rejected so the caller can retry.
 */
@Component
public class ExplanationParser {

    /** The reply could not be used. Never contains the reply text. */
    public static class InvalidExplanationException extends RuntimeException {
        public InvalidExplanationException(String message) {
            super(message);
        }
    }

    public record ParsedExplanation(String summary, Map<UUID, String> textsByMajorId) {
    }

    private final ObjectMapper objectMapper;

    public ExplanationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedExplanation parse(String raw, Set<UUID> expectedMajorIds) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stripCodeFence(raw));
        } catch (Exception e) {
            throw new InvalidExplanationException("reply is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidExplanationException("reply is not a JSON object");
        }
        String summary = root.path("summary").asText("").trim();
        if (summary.isEmpty()) {
            throw new InvalidExplanationException("summary is missing");
        }
        JsonNode majors = root.path("majors");
        if (!majors.isArray()) {
            throw new InvalidExplanationException("majors is not an array");
        }

        Map<UUID, String> texts = new HashMap<>();
        for (JsonNode item : majors) {
            UUID id = parseId(item.path("majorId").asText(""));
            String text = item.path("text").asText("").trim();
            if (id != null && expectedMajorIds.contains(id) && !text.isEmpty()) {
                texts.put(id, text);
            }
        }
        if (!texts.keySet().containsAll(expectedMajorIds)) {
            throw new InvalidExplanationException("some requested majors have no explanation");
        }
        // Keep only the requested majors, in a stable order
        Map<UUID, String> result = new LinkedHashMap<>();
        expectedMajorIds.forEach(id -> result.put(id, texts.get(id)));
        return new ParsedExplanation(summary, result);
    }

    private static UUID parseId(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Models often wrap JSON in a markdown fence even when told not to. */
    private static String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }
}
