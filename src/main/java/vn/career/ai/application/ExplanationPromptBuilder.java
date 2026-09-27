package vn.career.ai.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.career.recommendation.application.ExplanationApi;

/**
 * Fills {@code prompts/explanation.st} with the facts of one result. Uses plain placeholder replacement (not a
 * template engine) because the prompt itself contains JSON braces.
 */
@Component
public class ExplanationPromptBuilder {

    private final String template;

    public ExplanationPromptBuilder() {
        try (var in = new ClassPathResource("prompts/explanation.st").getInputStream()) {
            this.template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompts/explanation.st", e);
        }
    }

    public String build(ExplanationApi.ExplanationInput input) {
        return template
                .replace("{{dimensions}}", dimensions(input.dimensions()))
                .replace("{{majors}}", majors(input.majors()))
                .replace("{{reliabilityNote}}", input.lowReliability()
                        ? "\nLưu ý: bài khảo sát này có dấu hiệu trả lời chưa nghiêm túc (trả lời gần như giống nhau hoặc bỏ qua câu kiểm tra), "
                        + "nên độ tin cậy thấp. Hãy nói rõ điều này trong phần summary và khuyên bạn làm lại khảo sát.\n"
                        : "");
    }

    private static String dimensions(List<ExplanationApi.DimensionLine> dimensions) {
        return dimensions.stream()
                .map(d -> "- " + d.name() + ": " + formatPercent(d.percent()) + "%")
                .collect(Collectors.joining("\n"));
    }

    private static String majors(List<ExplanationApi.MajorLine> majors) {
        return majors.stream().map(ExplanationPromptBuilder::majorLine).collect(Collectors.joining("\n"));
    }

    private static String majorLine(ExplanationApi.MajorLine m) {
        Map<String, Object> b = m.breakdown();
        StringBuilder line = new StringBuilder("- majorId=").append(m.majorId())
                .append(" | hạng ").append(m.rank())
                .append(" | ").append(m.name())
                .append(" | điểm phù hợp ").append(format(m.score()))
                .append(" | sở thích ").append(format(b.get("interest")))
                .append(", năng khiếu ").append(format(b.get("aptitude")))
                .append(", giá trị nghề nghiệp ").append(format(b.get("values")));
        Object penalties = b.get("penalties");
        if (penalties instanceof List<?> list && !list.isEmpty()) {
            line.append(" | trừ điểm: ").append(list.stream()
                    .map(p -> p instanceof Map<?, ?> pm ? String.valueOf(pm.get("detail")) : String.valueOf(p))
                    .collect(Collectors.joining("; ")));
        }
        return line.toString();
    }

    private static String format(Object number) {
        return number instanceof Number n ? String.format(Locale.ROOT, "%.2f", n.doubleValue()) : "n/a";
    }

    private static String formatPercent(double percent) {
        return String.format(Locale.ROOT, "%.0f", percent);
    }
}
