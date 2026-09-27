package vn.career.common.audit;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.common.security.SecurityUtils;
import vn.career.common.web.ClientIp;

/**
 * Writes audit records inside the caller's transaction, so an action and its audit entry commit or roll back together.
 * Never put personal data (emails, scores, family info) in {@code metadata}.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /** Records an action performed by the current authenticated user (actor is null for anonymous callers). */
    @Transactional
    public void record(AuditAction action, String entityType, UUID entityId, Map<String, Object> metadata) {
        UUID actorId = SecurityUtils.currentUserId().orElse(null);
        repository.save(new AuditLog(actorId, action.name(), entityType, entityId, metadata, ClientIp.current()));
    }
}
