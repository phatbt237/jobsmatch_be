package vn.career.scoring.application;

import java.util.List;
import java.util.Map;

/** {@code qualityFlags} is the JSON shape stored on the attempt, {@code lowReliability} is derived from it. */
public record ScoringResult(List<DimensionResult> scores, Map<String, Object> qualityFlags, boolean lowReliability) {
}
