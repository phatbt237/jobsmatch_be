package vn.career.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.career.recommendation.application.ExplanationApi;

class ExplanationPromptBuilderTest {

    private final ExplanationPromptBuilder builder = new ExplanationPromptBuilder();
    private final UUID majorId = UUID.randomUUID();

    private ExplanationApi.ExplanationInput input(boolean lowReliability, List<?> penalties) {
        return new ExplanationApi.ExplanationInput(UUID.randomUUID(), lowReliability,
                List.of(new ExplanationApi.DimensionLine("I", "Nghiên cứu - Phân tích (Investigative)", "INTEREST", 88.0),
                        new ExplanationApi.DimensionLine("LOGIC", "Tư duy logic", "APTITUDE", 72.5)),
                List.of(new ExplanationApi.MajorLine(majorId, "Khoa học dữ liệu", 1, 0.8123,
                        Map.of("interest", 0.91, "aptitude", 0.8, "values", 0.7, "penalties", penalties))));
    }

    @Test
    void promptContainsTheScoresTheMajorsAndTheRules() {
        String prompt = builder.build(input(false, List.of()));

        assertThat(prompt).contains("Nghiên cứu - Phân tích (Investigative): 88%");
        assertThat(prompt).contains("Tư duy logic: 73%");
        assertThat(prompt).contains("majorId=" + majorId).contains("hạng 1").contains("Khoa học dữ liệu")
                .contains("điểm phù hợp 0.81").contains("sở thích 0.91").contains("năng khiếu 0.80");
        assertThat(prompt).contains("KHÔNG bịa số liệu").contains("xưng \"bạn\"").contains("3-4 câu")
                .contains("\"summary\"").contains("gợi ý tham khảo");
    }

    @Test
    void noPlaceholderIsLeftUnfilled() {
        assertThat(builder.build(input(false, List.of()))).doesNotContain("{{");
        assertThat(builder.build(input(true, List.of()))).doesNotContain("{{");
    }

    @Test
    void lowReliabilityAddsAWarning() {
        assertThat(builder.build(input(false, List.of()))).doesNotContain("độ tin cậy thấp");
        assertThat(builder.build(input(true, List.of()))).contains("độ tin cậy thấp").contains("làm lại");
    }

    @Test
    void penaltiesAreMentionedSoTheModelCanTellTheStudent() {
        String prompt = builder.build(input(false, List.of(Map.of("code", "BUDGET_EXCEEDED", "amount", 0.1,
                "detail", "Every university costs more than the budget"))));

        assertThat(prompt).contains("trừ điểm: Every university costs more than the budget");
    }
}
