package vn.career.catalog.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.career.common.event.AfterCommit;

/**
 * Read-through cache of public catalog responses in Redis (default TTL 1 hour).
 *
 * <p>Invalidation bumps a version counter that is part of every key, so one INCR makes all old entries unreachable
 * (they then expire on their own). The version is read before loading, so a slow reader can only ever write
 * under an outdated version. If Redis is down the loader is used directly: the cache never breaks the API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogCache {

    private static final String VERSION_KEY = "catalog:version";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CatalogProperties properties;

    public <T> T getOrLoad(String rawKey, TypeReference<T> type, Supplier<T> loader) {
        String key = null;
        try {
            String version = redis.opsForValue().get(VERSION_KEY);
            key = "catalog:" + (version == null ? "0" : version) + ":" + sha256(rawKey);
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, type);
            }
        } catch (Exception e) {
            log.warn("Catalog cache read failed ({}), loading from the database", e.getClass().getSimpleName());
        }
        T value = loader.get();
        if (key != null) {
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(value), properties.cacheTtl());
            } catch (Exception e) {
                log.warn("Catalog cache write failed ({})", e.getClass().getSimpleName());
            }
        }
        return value;
    }

    /** Invalidates every cached catalog response once the surrounding transaction has committed. */
    public void evictAll() {
        AfterCommit.run(() -> {
            try {
                redis.opsForValue().increment(VERSION_KEY);
            } catch (Exception e) {
                log.warn("Catalog cache eviction failed ({})", e.getClass().getSimpleName());
            }
        });
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
