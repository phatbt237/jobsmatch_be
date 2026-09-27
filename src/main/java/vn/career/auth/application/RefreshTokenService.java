package vn.career.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.domain.RefreshToken;
import vn.career.auth.domain.User;
import vn.career.auth.infrastructure.RefreshTokenRepository;
import vn.career.auth.infrastructure.security.JwtProperties;
import vn.career.common.exception.AppException;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.UnauthorizedException;

/** Issues, rotates and revokes opaque refresh tokens. Only SHA-256 hashes are persisted. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Result of a successful rotation: the token owner and the new raw refresh token. */
    public record Rotation(User user, String refreshToken) {
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now(clock).plus(jwtProperties.refreshTokenTtl());
        repository.save(new RefreshToken(user, hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * Exchanges a valid refresh token for a new one and revokes the old one. Presenting an already revoked token
     * means it was probably stolen, so every token of that user is revoked. noRollbackFor keeps that revocation
     * committed even though we throw afterwards.
     */
    @Transactional(noRollbackFor = AppException.class)
    public Rotation rotate(String rawToken) {
        String tokenHash = hash(rawToken);
        RefreshToken token = repository.findWithUserByTokenHash(tokenHash).orElseThrow(this::invalid);
        Instant now = Instant.now(clock);

        if (token.isRevoked()) {
            UUID userId = token.getUser().getId();
            repository.revokeAllForUser(userId, now);
            log.warn("Revoked refresh token was reused, all sessions of user {} revoked", userId);
            throw invalid();
        }
        if (token.isExpired(now)) {
            throw invalid();
        }
        // Atomic: if a concurrent request already rotated this token, we get 0 rows and must not issue another.
        if (repository.revokeIfActive(tokenHash, now) == 0) {
            throw invalid();
        }
        return new Rotation(token.getUser(), issue(token.getUser()));
    }

    /** Revokes the token if it belongs to {@code userId}. Unknown or foreign tokens are ignored (idempotent logout). */
    @Transactional
    public void revoke(String rawToken, UUID userId) {
        repository.findWithUserByTokenHash(hash(rawToken))
                .filter(token -> token.getUser().getId().equals(userId))
                .ifPresent(token -> repository.revokeIfActive(token.getTokenHash(), Instant.now(clock)));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now(clock));
    }

    private UnauthorizedException invalid() {
        return new UnauthorizedException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token is invalid or expired");
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
