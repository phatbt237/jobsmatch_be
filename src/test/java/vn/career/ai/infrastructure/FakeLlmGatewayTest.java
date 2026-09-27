package vn.career.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.career.ai.application.AiProperties;
import vn.career.ai.application.LlmGateway;
import vn.career.recommendation.application.ExplanationApi;

class FakeLlmGatewayTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeLlmGateway gateway = new FakeLlmGateway(mapper, new AiProperties(false, 1536,
            new AiProperties.Explanation(3, Duration.ofMillis(1)), new AiProperties.Chat(20, 10, 2000),
            new AiProperties.Rag(5, 500, 50, 0.0, false)));

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;   // vectors are normalised
    }

    private ExplanationApi.ExplanationInput input(UUID attemptId, UUID... majors) {
        List<ExplanationApi.MajorLine> lines = new java.util.ArrayList<>();
        int rank = 1;
        for (UUID major : majors) {
            lines.add(new ExplanationApi.MajorLine(major, "Ngành " + rank, rank++, 0.8, Map.of()));
        }
        return new ExplanationApi.ExplanationInput(attemptId, false,
                List.of(new ExplanationApi.DimensionLine("I", "Nghiên cứu", "INTEREST", 90)), lines);
    }

    private LlmGateway.ChatRequest chat(String message, List<LlmGateway.ContextChunk> context) {
        return new LlmGateway.ChatRequest("system", List.of(), context, message);
    }

    // ---------- explanation ----------

    @Test
    void explanationJsonCoversEveryMajor() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        var response = gateway.generateExplanation(new LlmGateway.ExplanationRequest("p", input(UUID.randomUUID(), a, b)));

        JsonNode json = mapper.readTree(response.text());
        assertThat(json.get("summary").asText()).isNotBlank();
        assertThat(json.get("majors")).hasSize(2);
        assertThat(json.get("majors").get(0).get("majorId").asText()).isEqualTo(a.toString());
        assertThat(response.model()).isEqualTo("fake-llm");
    }

    @Test
    void failuresCanBeInjectedPerAttempt() {
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        gateway.failNext(failing, 2);

        assertThatThrownBy(() -> gateway.generateExplanation(new LlmGateway.ExplanationRequest("p", input(failing))))
                .isInstanceOf(LlmGateway.LlmException.class);
        assertThatThrownBy(() -> gateway.generateExplanation(new LlmGateway.ExplanationRequest("p", input(failing))))
                .isInstanceOf(LlmGateway.LlmException.class);
        assertThat(gateway.generateExplanation(new LlmGateway.ExplanationRequest("p", input(failing))).text()).isNotBlank();
        // another attempt is never affected
        assertThat(gateway.generateExplanation(new LlmGateway.ExplanationRequest("p", input(healthy))).text()).isNotBlank();
        assertThat(gateway.explanationCalls(failing)).isEqualTo(3);
        assertThat(gateway.explanationCalls(healthy)).isEqualTo(1);
    }

    @Test
    void failAlwaysAndRawRepliesWork() {
        UUID id = UUID.randomUUID();
        gateway.replyWithRawOnce(id, "garbage");

        assertThat(gateway.generateExplanation(new LlmGateway.ExplanationRequest("p", input(id))).text()).isEqualTo("garbage");
        assertThat(gateway.generateExplanation(new LlmGateway.ExplanationRequest("p", input(id))).text()).startsWith("{");

        gateway.failAlways(id);
        assertThatThrownBy(() -> gateway.generateExplanation(new LlmGateway.ExplanationRequest("p", input(id))))
                .isInstanceOf(LlmGateway.LlmException.class);
    }

    // ---------- embeddings ----------

    @Test
    void embeddingsAreDeterministicNormalisedAndTheRightSize() {
        float[] first = gateway.embed("Lập trình viên phần mềm làm gì mỗi ngày");
        float[] second = gateway.embed("Lập trình viên phần mềm làm gì mỗi ngày");

        assertThat(first).hasSize(1536).isEqualTo(second);
        assertThat(cosine(first, first)).isBetween(0.999, 1.001);
        assertThat(gateway.embeddingDimensions()).isEqualTo(1536);
    }

    @Test
    void textsSharingWordsAreCloserThanUnrelatedTexts() {
        float[] question = gateway.embed("Nghề lập trình viên có áp lực không");
        float[] related = gateway.embed("Một ngày làm việc của lập trình viên: viết code, họp nhóm, sửa lỗi, áp lực deadline");
        float[] unrelated = gateway.embed("Điều dưỡng chăm sóc bệnh nhân ở bệnh viện, trực đêm và tiêm thuốc");

        assertThat(cosine(question, related)).isGreaterThan(cosine(question, unrelated));
    }

    @Test
    void diacriticsDoNotChangeTheEmbedding() {
        assertThat(gateway.embed("Kế toán")).isEqualTo(gateway.embed("Ke toan"));
    }

    @Test
    void emptyTextStillGivesAUsableVector() {
        float[] vector = gateway.embed("");

        assertThat(vector).hasSize(1536);
        assertThat(cosine(vector, vector)).isGreaterThan(0.99);
    }

    // ---------- chat ----------

    private static LlmGateway.ContextChunk pov(String text) {
        return new LlmGateway.ContextChunk("POV", UUID.randomUUID(), "Một ngày làm lập trình viên", text,
                Map.of("jobTitle", "Lập trình viên", "yearsExperience", 5));
    }

    @Test
    void chatAnswersFromContextAndAttributesPovToTheMentorsRole() {
        var answer = gateway.chat(chat("Nghề lập trình viên có áp lực không?", List.of(pov("Áp lực deadline khá lớn."))));

        assertThat(answer.text()).contains("Theo chia sẻ của một Lập trình viên có 5 năm kinh nghiệm")
                .contains("Áp lực deadline khá lớn.");
    }

    @Test
    void chatAdmitsWhenThereIsNoData() {
        var answer = gateway.chat(chat("Ngành này có dễ xin việc không?", List.of()));

        assertThat(answer.text()).isEqualTo(FakeLlmGateway.NO_DATA);
        assertThat(answer.usedContext()).isFalse();
    }

    @Test
    void chatPolitelyRefusesOffTopicQuestionsAndDoesNotClaimToHaveUsedTheContext() {
        var soccer = gateway.chat(chat("Hôm nay bóng đá ai thắng?", List.of(pov("x"))));

        assertThat(soccer.text()).isEqualTo(FakeLlmGateway.REFUSAL);
        assertThat(soccer.usedContext()).isFalse();
        assertThat(gateway.chat(chat("Cho mình công thức nấu phở", List.of(pov("x")))).text()).isEqualTo(FakeLlmGateway.REFUSAL);
    }

    @Test
    void chatPrefersAMentorsFirstHandAccountOverGeneralMajorInformation() {
        var major = new LlmGateway.ContextChunk("MAJOR", UUID.randomUUID(), "Kế toán", "Mô tả ngành.", Map.of());

        var answer = gateway.chat(chat("Ngành kế toán áp lực không?", List.of(major, pov("Mùa quyết toán khá căng."))));

        assertThat(answer.text()).contains("Theo chia sẻ của một Lập trình viên có 5 năm kinh nghiệm");
        assertThat(answer.usedContext()).isTrue();
    }

    @Test
    void majorInformationIsQuotedWithoutAPersonalAttribution() {
        var chunk = new LlmGateway.ContextChunk("MAJOR", UUID.randomUUID(), "Kế toán", "Ngành đào tạo kế toán viên.", Map.of());

        String text = gateway.chat(chat("Ngành kế toán học gì?", List.of(chunk))).text();

        assertThat(text).contains("Theo thông tin về Kế toán").doesNotContain("Theo chia sẻ của một");
    }
}
