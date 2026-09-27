package vn.career.common.api;

import java.time.Instant;
import java.util.List;
import vn.career.common.exception.ErrorCode;

/** Standard envelope of every API response. */
public record ApiResponse<T>(boolean success, T data, ErrorBody error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return error(code, message, List.of());
    }

    public static ApiResponse<Void> error(ErrorCode code, String message, List<ErrorDetail> details) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), message, details), Instant.now());
    }
}
