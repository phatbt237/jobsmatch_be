package vn.career.common.exception;

/** Too many requests (HTTP 429). The API also sends a {@code Retry-After} header with {@code retryAfterSeconds}. */
public class RateLimitException extends AppException {

    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
