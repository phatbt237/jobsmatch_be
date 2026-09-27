package vn.career.scoring.domain;

/** Score of one dimension: the weighted sum, the lowest and highest possible sums for the answered questions, and 0..1. */
public record DimensionScore(String dimension, double raw, double min, double max, double normalized) {
}
