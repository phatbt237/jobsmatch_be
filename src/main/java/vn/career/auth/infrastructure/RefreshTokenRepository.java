package vn.career.auth.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.career.auth.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
    Optional<RefreshToken> findWithUserByTokenHash(@Param("hash") String hash);

    /** Atomically revokes a still-active token. Returns 0 if someone else revoked it first. */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now "
            + "where t.tokenHash = :hash and t.revokedAt is null")
    int revokeIfActive(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now "
            + "where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
