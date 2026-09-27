package vn.career.auth.application;

import java.util.Optional;
import java.util.UUID;

/** Account role questions and changes for other modules (mentor onboarding). Roles are exchanged as names. */
public interface AccountRoleApi {

    /** Role name (STUDENT, PARENT, MENTOR, ADMIN) or empty if the user does not exist. */
    Optional<String> roleOf(UUID userId);

    /** Roles of many users at once, keyed by id (unknown ids are left out). */
    java.util.Map<UUID, String> rolesOf(java.util.Collection<UUID> userIds);

    /**
     * Changes the role and writes a ROLE_CHANGED audit entry. Existing access tokens keep the old role until they
     * expire (15 minutes) or are refreshed.
     */
    void changeRole(UUID userId, String newRole);
}
