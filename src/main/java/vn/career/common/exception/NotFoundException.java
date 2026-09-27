package vn.career.common.exception;

/** The requested resource does not exist (HTTP 404). */
public class NotFoundException extends AppException {

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
