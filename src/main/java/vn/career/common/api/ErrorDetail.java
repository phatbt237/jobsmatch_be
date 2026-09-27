package vn.career.common.api;

/** One entry of {@code error.details}, usually a field-level validation problem. */
public record ErrorDetail(String field, String message) {
}
