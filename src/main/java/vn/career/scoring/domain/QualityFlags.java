package vn.career.scoring.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Data-quality checks of one attempt. Attention failures and straight-lining make the result low reliability. */
public record QualityFlags(boolean attentionFailed, boolean straightLining, boolean tooFast) {

    public boolean lowReliability() {
        return attentionFailed || straightLining;
    }

    /** JSON shape stored in survey_attempts.quality_flags. */
    public Map<String, Object> toMap() {
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("ATTENTION_FAILED", attentionFailed);
        flags.put("STRAIGHT_LINING", straightLining);
        flags.put("TOO_FAST", tooFast);
        flags.put("lowReliability", lowReliability());
        return flags;
    }
}
