package vn.career.common.exception;

/** The caller is authenticated but not allowed to do this (HTTP 403). */
public class ForbiddenException extends AppException {

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }
}
