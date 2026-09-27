package vn.career.scoring.application;

/** Score of one dimension. {@code normalized} is within 0..1. */
public record DimensionResult(String code, double raw, double normalized) {
}
