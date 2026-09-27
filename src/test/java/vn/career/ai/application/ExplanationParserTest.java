package vn.career.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExplanationParserTest {

    private final ExplanationParser parser = new ExplanationParser(new ObjectMapper());
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private String json(String summary, UUID id1, String text1, UUID id2, String text2) {
        return "{\"summary\":\"" + summary + "\",\"majors\":[{\"majorId\":\"" + id1 + "\",\"text\":\"" + text1
                + "\"},{\"majorId\":\"" + id2 + "\",\"text\":\"" + text2 + "\"}]}";
    }

    @Test
    void validReplyIsParsed() {
        var parsed = parser.parse(json("Tổng quan", a, "Vì A", b, "Vì B"), Set.of(a, b));

        assertThat(parsed.summary()).isEqualTo("Tổng quan");
        assertThat(parsed.textsByMajorId()).containsEntry(a, "Vì A").containsEntry(b, "Vì B");
    }

    @Test
    void markdownCodeFencesAreStripped() {
        String fenced = "```json\n" + json("S", a, "A", b, "B") + "\n```";

        assertThat(parser.parse(fenced, Set.of(a, b)).textsByMajorId()).hasSize(2);
        assertThat(parser.parse("```\n" + json("S", a, "A", b, "B") + "\n```", Set.of(a, b)).summary()).isEqualTo("S");
    }

    @Test
    void extraMajorsAreIgnoredAndOnlyRequestedOnesKept() {
        UUID extra = UUID.randomUUID();

        var parsed = parser.parse(json("S", a, "A", extra, "X"), Set.of(a));

        assertThat(parsed.textsByMajorId()).containsOnlyKeys(a);
    }

    @Test
    void textIsTrimmed() {
        assertThat(parser.parse(json("  S  ", a, "  A  ", b, "B"), Set.of(a, b)).textsByMajorId().get(a)).isEqualTo("A");
    }

    @Test
    void invalidJsonIsRejected() {
        assertThatThrownBy(() -> parser.parse("this is not json", Set.of(a)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class);
        assertThatThrownBy(() -> parser.parse("{ \"summary\": ", Set.of(a)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class);
        assertThatThrownBy(() -> parser.parse(null, Set.of(a)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class);
        assertThatThrownBy(() -> parser.parse("[1,2]", Set.of(a)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class);
    }

    @Test
    void missingOrBlankSummaryIsRejected() {
        assertThatThrownBy(() -> parser.parse("{\"majors\":[{\"majorId\":\"" + a + "\",\"text\":\"A\"}]}", Set.of(a)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class).hasMessageContaining("summary");
        assertThatThrownBy(() -> parser.parse(json("   ", a, "A", b, "B"), Set.of(a, b)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class);
    }

    @Test
    void aMissingMajorIsRejected() {
        assertThatThrownBy(() -> parser.parse("{\"summary\":\"S\",\"majors\":[{\"majorId\":\"" + a + "\",\"text\":\"A\"}]}",
                Set.of(a, b))).isInstanceOf(ExplanationParser.InvalidExplanationException.class);
    }

    @Test
    void aBlankTextOrWrongIdCountsAsMissing() {
        assertThatThrownBy(() -> parser.parse(json("S", a, "A", b, "   "), Set.of(a, b)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class);
        assertThatThrownBy(() -> parser.parse(json("S", a, "A", UUID.randomUUID(), "B"), Set.of(a, b)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class);
        assertThatThrownBy(() -> parser.parse("{\"summary\":\"S\",\"majors\":[{\"majorId\":\"not-a-uuid\",\"text\":\"A\"}]}",
                Set.of(a))).isInstanceOf(ExplanationParser.InvalidExplanationException.class);
    }

    @Test
    void majorsThatIsNotAnArrayIsRejected() {
        assertThatThrownBy(() -> parser.parse("{\"summary\":\"S\",\"majors\":\"none\"}", Set.of(a)))
                .isInstanceOf(ExplanationParser.InvalidExplanationException.class);
    }

    @Test
    void theErrorMessageNeverContainsTheReply() {
        assertThatThrownBy(() -> parser.parse("SECRET-CONTENT-123", Set.of(a)))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET"));
    }
}
