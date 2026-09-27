package vn.career.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.career.ai.application.AiProperties;
import vn.career.ai.application.LlmGateway;
import vn.career.recommendation.application.ExplanationApi;

/**
 * Stand-in for a real model: used by the test profile and in development when no API key is configured
 * ({@code app.ai.enabled=false}, the default). It returns fixed, template based text and a deterministic
 * bag-of-words "embedding" so retrieval still finds related passages.
 *
 * <p>For tests it can be told to fail, be slow or return garbage for one attempt only (keyed by attempt id, so
 * background work of other tests is never affected).
 */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class FakeLlmGateway implements LlmGateway {

    static final String MODEL = "fake-llm";

    static final String REFUSAL = "Xin lỗi, mình chỉ hỗ trợ các câu hỏi về hướng nghiệp và học tập "
            + "(chọn ngành, trường, tổ hợp môn, điểm chuẩn, công việc...). Bạn hỏi mình về những chủ đề đó nhé!";
    static final String NO_DATA = "Hiện mình chưa có dữ liệu để trả lời câu hỏi này. "
            + "Bạn thử hỏi cụ thể hơn về một ngành, hoặc đọc thêm các bài chia sẻ của người làm trong ngành nhé.";

    private static final Set<String> IN_SCOPE_WORDS = Set.of("nganh", "nghe", "truong", "dai hoc", "hoc", "diem chuan",
            "tuyen sinh", "to hop", "cong viec", "viec lam", "luong", "hoc phi", "sinh vien", "ky nang", "huong nghiep",
            "chon nganh", "mentor", "chia se", "kinh nghiem", "chuong trinh", "tot nghiep", "thi", "mon");

    private static class Behavior {
        final AtomicInteger calls = new AtomicInteger();
        volatile int failuresLeft;
        volatile boolean failAlways;
        volatile Duration delay = Duration.ZERO;
        final Queue<String> rawReplies = new ArrayDeque<>();
    }

    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final Map<UUID, Behavior> behaviors = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- test controls

    /** The next {@code times} explanation calls for this attempt throw {@link LlmException}. */
    public void failNext(UUID attemptId, int times) {
        behavior(attemptId).failuresLeft = times;
    }

    public void failAlways(UUID attemptId) {
        behavior(attemptId).failAlways = true;
    }

    /** Every explanation call for this attempt waits this long first. */
    public void delay(UUID attemptId, Duration delay) {
        behavior(attemptId).delay = delay;
    }

    /** The next explanation call for this attempt returns this text instead of proper JSON. */
    public void replyWithRawOnce(UUID attemptId, String raw) {
        Behavior b = behavior(attemptId);
        synchronized (b.rawReplies) {
            b.rawReplies.add(raw);
        }
    }

    public int explanationCalls(UUID attemptId) {
        Behavior b = behaviors.get(attemptId);
        return b == null ? 0 : b.calls.get();
    }

    private Behavior behavior(UUID attemptId) {
        return behaviors.computeIfAbsent(attemptId, id -> new Behavior());
    }

    // ---------------------------------------------------------------- LlmGateway

    @Override
    public LlmResponse generateExplanation(ExplanationRequest request) {
        Behavior b = behavior(request.input().attemptId());
        b.calls.incrementAndGet();
        if (!b.delay.isZero()) {
            try {
                Thread.sleep(b.delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("interrupted");
            }
        }
        if (b.failAlways || b.failuresLeft > 0) {
            b.failuresLeft = Math.max(0, b.failuresLeft - 1);
            throw new LlmException("fake LLM failure");
        }
        synchronized (b.rawReplies) {
            String raw = b.rawReplies.poll();
            if (raw != null) {
                return new LlmResponse(raw, MODEL);
            }
        }
        return new LlmResponse(explanationJson(request.input()), MODEL);
    }

    private String explanationJson(ExplanationApi.ExplanationInput input) {
        ObjectNode root = objectMapper.createObjectNode();
        ExplanationApi.DimensionLine top = input.dimensions().stream()
                .max(java.util.Comparator.comparingDouble(ExplanationApi.DimensionLine::percent)).orElse(null);
        String summary = "Bạn nổi bật ở " + (top == null ? "nhiều mặt" : top.name() + " (" + Math.round(top.percent()) + "%)")
                + ". " + (input.lowReliability()
                ? "Bài khảo sát có dấu hiệu chưa nghiêm túc nên độ tin cậy thấp, bạn nên làm lại. " : "")
                + "Đây là gợi ý tham khảo, hãy đọc thêm chia sẻ của người trong nghề.";
        root.put("summary", summary);
        ArrayNode majors = root.putArray("majors");
        for (ExplanationApi.MajorLine major : input.majors()) {
            ObjectNode item = majors.addObject();
            item.put("majorId", major.majorId().toString());
            item.put("text", "Ngành " + major.name() + " xếp hạng " + major.rank() + " vì điểm phù hợp của bạn đạt "
                    + Math.round(major.score() * 100) + "%. Điểm này đến từ sở thích, năng khiếu và giá trị nghề nghiệp "
                    + "mà bạn đã chọn. (Nội dung mẫu do FakeLlmGateway tạo ra.)");
        }
        return root.toString();
    }

    @Override
    public ChatAnswer chat(ChatRequest request) {
        String message = normalize(request.userMessage());
        if (IN_SCOPE_WORDS.stream().noneMatch(message::contains)) {
            return new ChatAnswer(REFUSAL, MODEL, false);
        }
        if (request.context().isEmpty()) {
            return new ChatAnswer(NO_DATA, MODEL, false);
        }
        // Like a good model, prefer a mentor's first-hand account when the context has one
        ContextChunk top = request.context().stream().filter(c -> "POV".equals(c.sourceType())).findFirst()
                .orElse(request.context().get(0));
        String snippet = top.text().length() > 400 ? top.text().substring(0, 400) + "..." : top.text();
        String answer;
        if ("POV".equals(top.sourceType())) {
            Object jobTitle = top.metadata().getOrDefault("jobTitle", "người làm trong ngành");
            Object years = top.metadata().getOrDefault("yearsExperience", "nhiều");
            answer = "Theo chia sẻ của một " + jobTitle + " có " + years + " năm kinh nghiệm: " + snippet;
        } else {
            answer = "Theo thông tin về " + top.title() + ": " + snippet;
        }
        return new ChatAnswer(answer + " (Câu trả lời mẫu do FakeLlmGateway tạo ra.)", MODEL, true);
    }

    /** Hashing bag-of-words: texts sharing words get similar vectors, so cosine search behaves sensibly. */
    @Override
    public float[] embed(String text) {
        int dimensions = embeddingDimensions();
        float[] vector = new float[dimensions];
        for (String word : normalize(text).split("[^a-z0-9]+")) {
            if (word.length() > 1) {
                vector[Math.floorMod(word.hashCode() * 31 + word.length(), dimensions)] += 1f;
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0) {
            vector[0] = 1f;   // an all-zero vector has no direction; pgvector rejects it for cosine distance
            return vector;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    @Override
    public int embeddingDimensions() {
        return properties.embeddingDimensions();
    }

    @Override
    public String modelName() {
        return MODEL;
    }

    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'd').toLowerCase(Locale.ROOT);
    }
}
