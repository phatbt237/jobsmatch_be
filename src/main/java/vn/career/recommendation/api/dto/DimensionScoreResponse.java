package vn.career.recommendation.api.dto;

/** {@code percent} is the normalised score of the dimension, 0 to 100 with one decimal. */
public record DimensionScoreResponse(String code, String name, String group, double percent) {
}
