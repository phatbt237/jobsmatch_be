package vn.career.ai.infrastructure;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.career.ai.application.AiProperties;
import vn.career.common.exception.RateLimitException;

/** Limits how many chatbot messages one student can send per hour (default 20), counted in Redis. */
@Component
@RequiredArgsConstructor
public class ChatRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final AiProperties properties;

    /** Counts one message. Throws 429 (with Retry-After) once the hourly allowance is used up. */
    public void countOrReject(UUID userId) {
        String key = "chat:rate:" + userId;
        Long count = redis.opsForValue().increment(key);
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            redis.expire(key, WINDOW);   // first message of the window (or a key that lost its expiry)
            ttl = WINDOW.toSeconds();
        }
        if (count != null && count > properties.chat().messagesPerHour()) {
            throw new RateLimitException("You have reached the limit of " + properties.chat().messagesPerHour()
                    + " chat messages per hour, please try again later", ttl);
        }
    }
}
