package vn.career.content.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.career.common.api.ErrorDetail;

/** The five fixed parts of a POV post and the rules for them. Pure logic. */
public final class PovSections {

    public static final int MAX_LENGTH = 5000;

    /** Key to the Vietnamese heading used when the post is turned into plain text. */
    public static final Map<String, String> HEADINGS = new LinkedHashMap<>();

    static {
        HEADINGS.put("a_day_at_work", "Một ngày làm việc");
        HEADINGS.put("wish_i_knew", "Điều mình ước biết sớm hơn");
        HEADINGS.put("dark_side", "Mặt trái của nghề");
        HEADINGS.put("who_fits", "Ai phù hợp với nghề này");
        HEADINGS.put("school_vs_work", "Học ở trường và đi làm khác nhau thế nào");
    }

    private PovSections() {
    }

    /** Problems that make the sections unacceptable even for a draft: unknown keys and over-long text. */
    public static List<ErrorDetail> validateForSave(Map<String, String> sections) {
        List<ErrorDetail> problems = new ArrayList<>();
        sections.forEach((key, value) -> {
            if (!HEADINGS.containsKey(key)) {
                problems.add(new ErrorDetail("sections." + key, "unknown section, allowed: " + String.join(", ", HEADINGS.keySet())));
            } else if (value != null && value.length() > MAX_LENGTH) {
                problems.add(new ErrorDetail("sections." + key, "must be at most " + MAX_LENGTH + " characters"));
            }
        });
        return problems;
    }

    /** Extra problems that stop a post being sent for review: every section must have text. */
    public static List<ErrorDetail> validateForSubmit(Map<String, String> sections) {
        List<ErrorDetail> problems = new ArrayList<>();
        for (String key : HEADINGS.keySet()) {
            String value = sections.get(key);
            if (value == null || value.isBlank()) {
                problems.add(new ErrorDetail("sections." + key, "is required before submitting for review"));
            }
        }
        return problems;
    }

    /** Plain text of the post, used for search and chat. Sections without text are left out. */
    public static String toPlainText(String title, Map<String, String> sections) {
        StringBuilder text = new StringBuilder(title).append("\n\n");
        HEADINGS.forEach((key, heading) -> {
            String value = sections.get(key);
            if (value != null && !value.isBlank()) {
                text.append(heading).append(": ").append(value.trim()).append("\n\n");
            }
        });
        return text.toString().trim();
    }
}
