package vn.career.common.exception;

/** The caller is not authenticated or presented bad credentials (HTTP 401). */
public class UnauthorizedException extends AppException {

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
