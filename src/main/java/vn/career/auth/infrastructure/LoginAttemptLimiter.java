package vn.career.auth.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.career.auth.infrastructure.security.LoginLimitProperties;
import vn.career.common.exception.BusinessException;
import vn.career.common.exception.ErrorCode;

/**
 * Counts failed logins per email in Redis. The counter lives for one lockout window starting at the first failure.
 * The Redis key holds a hash of the email so no address is stored in plain text.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    private static final String KEY_PREFIX = "login:fail:";

    private final StringRedisTemplate redis;
    private final LoginLimitProperties properties;

    /** Throws 429 if this email already used up its attempts. Call before checking the password. */
    public void assertNotLocked(String email) {
        String value = redis.opsForValue().get(key(email));
        if (value != null && Long.parseLong(value) >= properties.maxAttempts()) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
                    "Too many failed login attempts, please try again later");
        }
    }

    public void recordFailure(String email) {
        String key = key(email);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, properties.lockout());
        }
    }

    public void reset(String email) {
        redis.delete(key(email));
    }

    private static String key(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
