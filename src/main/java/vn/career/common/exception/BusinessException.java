package vn.career.common.exception;

/** A business rule was violated (HTTP 422 unless the error code says otherwise). */
public class BusinessException extends AppException {

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
