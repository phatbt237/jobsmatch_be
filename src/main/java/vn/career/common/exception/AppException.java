package vn.career.common.exception;

import java.util.List;
import lombok.Getter;
import vn.career.common.api.ErrorDetail;

/** Base class for all expected (client-facing) errors. The HTTP status comes from the {@link ErrorCode}. */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<ErrorDetail> details;

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public AppException(ErrorCode errorCode, String message, List<ErrorDetail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }
}
