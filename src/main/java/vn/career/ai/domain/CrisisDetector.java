package vn.career.ai.domain;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Cheap safety net that runs before the LLM: recognises messages that suggest a student may be in serious distress
 * (self-harm, suicide). Such messages get a fixed, caring reply that points to trusted people and professionals.
 * It is deliberately conservative and is NOT a substitute for the model's own judgement (see the system prompt).
 * Phrases with diacritics are matched as typed; a few unambiguous ones are also matched without diacritics.
 * "tu tu" is not matched without diacritics because it is also "từ từ" (slowly).
 */
public final class CrisisDetector {

    private static final List<String> WITH_DIACRITICS = List.of("tự tử", "tự sát", "muốn chết", "không muốn sống",
            "kết thúc cuộc sống", "kết thúc cuộc đời", "tự làm đau", "tự làm hại", "rạch tay", "không muốn tồn tại",
            "chết đi cho rồi", "sống không còn ý nghĩa");

    private static final List<String> WITHOUT_DIACRITICS = List.of("tu sat", "muon chet", "khong muon song",
            "ket thuc cuoc doi", "tu lam dau", "tu lam hai", "rach tay");

    /** Fixed reply. Warm, no advice beyond reaching out, and it does not repeat the student's words. */
    public static final String SUPPORT_MESSAGE = "Mình rất tiếc khi nghe bạn đang cảm thấy như vậy, và mình cảm ơn bạn đã chia sẻ. "
            + "Bạn không phải một mình. Hãy nói chuyện ngay với một người bạn tin tưởng như ba mẹ, người thân hoặc thầy cô, "
            + "hoặc tìm đến chuyên gia tâm lý để được giúp đỡ. Nếu bạn đang gặp nguy hiểm, hãy gọi cấp cứu 115 hoặc "
            + "đường dây tư vấn của Tổng đài quốc gia bảo vệ trẻ em 111 (miễn phí, 24/7). "
            + "Chuyện chọn ngành có thể để sau, sức khỏe và sự an toàn của bạn quan trọng hơn.";

    private CrisisDetector() {
    }

    public static boolean isCrisis(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (WITH_DIACRITICS.stream().anyMatch(lower::contains)) {
            return true;
        }
        String plain = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd');
        // only text that was really typed without diacritics: if diacritics were stripped by us, the original had them
        return lower.equals(plain) && WITHOUT_DIACRITICS.stream().anyMatch(plain::contains);
    }
}
