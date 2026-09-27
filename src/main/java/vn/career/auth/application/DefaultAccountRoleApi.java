package vn.career.auth.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.career.auth.domain.Role;
import vn.career.auth.domain.User;
import vn.career.auth.infrastructure.UserRepository;
import vn.career.common.audit.AuditAction;
import vn.career.common.audit.AuditService;
import vn.career.common.exception.ErrorCode;
import vn.career.common.exception.NotFoundException;

@Service
@RequiredArgsConstructor
class DefaultAccountRoleApi implements AccountRoleApi {

    private final UserRepository users;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> roleOf(UUID userId) {
        return users.findById(userId).map(u -> u.getRole().name());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> rolesOf(java.util.Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u.getRole().name()));
    }

    @Override
    @Transactional
    public void changeRole(UUID userId, String newRole) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
        Role target = Role.valueOf(newRole);
        if (user.getRole() == target) {
            return;
        }
        Role previous = user.getRole();
        user.changeRole(target);
        users.flush();
        auditService.record(AuditAction.ROLE_CHANGED, "User", userId,
                Map.of("from", previous.name(), "to", target.name()));
    }
}
